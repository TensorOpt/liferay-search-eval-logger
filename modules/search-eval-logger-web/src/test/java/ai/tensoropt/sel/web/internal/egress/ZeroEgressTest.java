/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.egress;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * D9: zero egress. The plugin never initiates an outbound network request,
 * neither from the server nor from the admin's browser; every link to
 * TensorOpt is a static, user-clicked hyperlink (DESIGN.md Section 10).
 *
 * <p>
 * No HTTP client is introduced to implement TO-112, and this is what
 * verifies that claim rather than asserting it in prose. It scans the whole
 * plugin's own code, not a sample: every <code>.java</code> file in this
 * module and in <code>search-eval-logger-impl</code> (the collector and the
 * notifier, the two places most likely to grow a "phone home" call) for an
 * import or a call capable of making an outbound connection, and every
 * <code>.jsp</code> and <code>.js</code> file under this module's
 * <code>META-INF/resources</code> (the only place the plugin's own markup or
 * script lives) for a <code>fetch</code> or <code>XMLHttpRequest</code> call
 * whose target is a literal external address rather than a server-rendered
 * portlet URL.
 * </p>
 *
 * <p>
 * One test, in one module, for both modules' Java sources: a shared
 * test-fixtures module for a forty line scan would be more machinery than
 * the thing it replaces, and web already depends on nothing from impl, so
 * reaching its sibling directory by a relative path costs nothing a real
 * reuse would need protecting. The <code>api</code> and <code>service</code>
 * modules are not scanned: the first holds interfaces, enums and value
 * objects with no I/O of any kind, and the second is Service Builder
 * persistence; neither has anywhere an outbound call could plausibly be
 * added without the change being obvious on sight in a one line method.
 * </p>
 *
 * <p>
 * Static and source level on purpose. A bytecode or runtime scan would also
 * catch reflection, but nothing here has a reason to reach for it, and a
 * source scan is the cheapest thing that is still genuine: it fails on the
 * import or the call itself, not on the code having to run.
 * </p>
 */
public class ZeroEgressTest {

	@Test
	public void javaSourceNeverImportsAnOutboundHttpClient() throws IOException {
		List<String> offenders = new ArrayList<>();

		for (Path sourceRoot : _javaSourceRoots()) {
			try (Stream<Path> paths = Files.walk(sourceRoot)) {
				paths.filter(
					path -> path.toString().endsWith(".java")
				).forEach(
					path -> _scanJava(path, offenders)
				);
			}
		}

		assertTrue(
			offenders.isEmpty(),
			"DESIGN.md D9 (zero egress): found an import or a call capable " +
				"of making an outbound connection: " + offenders);
	}

	@Test
	public void jspAndJavaScriptNeverFetchALiteralExternalAddress()
		throws IOException {

		Path resourcesRoot = Paths.get(
			"src", "main", "resources", "META-INF", "resources");

		assertTrue(
			Files.isDirectory(resourcesRoot),
			"Expected to find " + resourcesRoot.toAbsolutePath() +
				"; this test must run with the web module directory as the " +
					"working directory, which Gradle's test task already " +
						"sets");

		List<String> offenders = new ArrayList<>();

		try (Stream<Path> paths = Files.walk(resourcesRoot)) {
			paths.filter(
				path -> {
					String name = path.toString();

					return name.endsWith(".jsp") || name.endsWith(".js");
				}
			).forEach(
				path -> _scanMarkup(path, offenders)
			);
		}

		assertTrue(
			offenders.isEmpty(),
			"DESIGN.md D9 (zero egress): found a fetch/XHR call targeting " +
				"what looks like a literal external address rather than a " +
					"server-rendered portlet URL: " + offenders);
	}

	/**
	 * This module's own sources plus its sibling impl module's, reached by a
	 * relative path: see the class comment for why that is the chosen home
	 * rather than a shared test-fixtures module.
	 */
	private List<Path> _javaSourceRoots() {
		Path ownRoot = Paths.get("src", "main", "java");
		Path implRoot = Paths.get(
			"..", "search-eval-logger-impl", "src", "main", "java");

		for (Path root : Arrays.asList(ownRoot, implRoot)) {
			assertTrue(
				Files.isDirectory(root),
				"Expected to find " + root.toAbsolutePath() +
					"; this test must run with the web module directory as " +
						"the working directory, which Gradle's test task " +
							"already sets, and assumes the impl module is " +
								"its usual sibling under modules/");
		}

		return Arrays.asList(ownRoot, implRoot);
	}

	private void _scanJava(Path path, List<String> offenders) {
		String content = _read(path);

		for (String forbidden : _FORBIDDEN_JAVA) {
			if (content.contains(forbidden)) {
				offenders.add(path + " references " + forbidden);
			}
		}
	}

	/**
	 * A <code>fetch</code> or <code>XMLHttpRequest.open</code> call is fine
	 * when its target is a server-rendered portlet URL (an EL expression, a
	 * scriptlet, or a same-page relative path) and not fine when it is a
	 * literal absolute address: that is an outbound request this plugin's
	 * own markup would be making on its own, the thing D9 forbids. A call
	 * whose target is some other JavaScript expression (a variable, a
	 * function call) is not flagged either way; it cannot be judged
	 * statically, and nothing in this plugin's markup has a legitimate
	 * reason to build a fetch target out of anything but a portlet URL.
	 */
	private void _scanMarkup(Path path, List<String> offenders) {
		String content = _read(path);

		Matcher matcher = _FETCH_CALL.matcher(content);

		while (matcher.find()) {
			String target = matcher.group(1);

			if (_LITERAL_EXTERNAL_TARGET.matcher(target).find()) {
				offenders.add(
					path + " fetches a literal address: " + target);
			}
		}
	}

	private String _read(Path path) {
		try {
			return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		}
		catch (IOException ioException) {
			throw new UncheckedIOException(ioException);
		}
	}

	/**
	 * A quoted string literal immediately opening a <code>fetch(</code> or
	 * <code>.open("METHOD", ...)</code>-style call. Deliberately narrow: it
	 * only looks at the first argument when it is a plain quoted string,
	 * which is exactly the shape a hand-written literal URL would have to
	 * take, and exactly the shape this plugin's own
	 * <code>fetch('${dismissSignupBannerURL}', ...)</code> already has.
	 */
	private static final Pattern _FETCH_CALL = Pattern.compile(
		"fetch\\(\\s*['\"]([^'\"]*)['\"]");

	/**
	 * Forbidden Java imports and calls: the JDK's own HTTP clients, raw
	 * sockets, the common third party libraries, and
	 * <code>com.liferay.portal.kernel.util.HttpUtil</code> /
	 * <code>Http</code>, Liferay's own convenience wrappers around exactly
	 * the same thing and public API this plugin could otherwise reach for
	 * without adding a dependency. Matched as full import statements, not
	 * bare class names, so that a class merely starting with the same
	 * letters (there is no such class here, but there could be one day)
	 * cannot be mistaken for one of these.
	 */
	private static final List<String> _FORBIDDEN_JAVA = Arrays.asList(
		"import java.net.http.", "import java.net.HttpURLConnection;",
		"import java.net.URLConnection;", "import java.net.Socket;",
		"import org.apache.http.", "import org.apache.hc.client5.",
		"import okhttp3.", "import com.squareup.okhttp",
		"import com.liferay.portal.kernel.util.HttpUtil;",
		"import com.liferay.portal.kernel.util.Http;",

		// Calls made through a fully qualified name, with no import to
		// catch above.

		".openConnection(", ".openStream(", "new Socket(");

	/**
	 * A fetch target that names an explicit host: an absolute
	 * <code>http(s)://</code> address or a protocol-relative
	 * <code>//host/...</code> one. A plain relative path
	 * (<code>/o/...</code>) and a server-rendered EL expression
	 * (<code>${...}</code>) are both same-origin by construction and match
	 * neither.
	 */
	private static final Pattern _LITERAL_EXTERNAL_TARGET = Pattern.compile(
		"^(https?:)?//");

}
