/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.cohort;

import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import java.time.Clock;

import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.osgi.service.component.annotations.Component;

/**
 * Produces the <code>cohortHash</code> of DESIGN.md 4.3: a salted hash of the
 * user ID, with the salt rotating on a fixed schedule.
 *
 * <p>
 * The salt lives in memory only. It is never written to the database and never
 * exported, and a superseded salt is overwritten rather than archived, so no
 * process can re-link cohorts across a rotation. A restart therefore also
 * rotates, which shortens the linkable span rather than lengthening it: an
 * evaluator loses some grouping power, and nobody gains a stable pseudonymous
 * identifier. That is the safe direction of error for the whole scheme.
 * </p>
 *
 * <p>
 * Rotation is checked lazily on read instead of being driven by a scheduled
 * job. A salt only matters while events are being written, so a timer that
 * fires on an idle instance buys nothing, and a missed fire on a busy one would
 * be a silent privacy regression.
 * </p>
 *
 * <p>
 * The salt is per virtual instance, matching the scope of the rotation period
 * setting. Cohorts are never compared across instances.
 * </p>
 */
@Component(service = CohortSaltRegistry.class)
public class CohortSaltRegistry {

	/**
	 * Returns a 64 character hex digest that is stable for one user within one
	 * rotation window and unrelatable outside it.
	 */
	public String hash(long companyId, long userId, int rotationDays) {
		long rotationMillis = TimeUnit.DAYS.toMillis(Math.max(rotationDays, 1));

		byte[] salt = _getSalt(companyId, rotationMillis);

		MessageDigest messageDigest = _getMessageDigest();

		messageDigest.update(salt);
		messageDigest.update(
			String.valueOf(userId).getBytes(StandardCharsets.UTF_8));

		return HexFormat.of().formatHex(messageDigest.digest());
	}

	/**
	 * Visible for testing, so rotation can be driven without sleeping.
	 */
	void setClock(Clock clock) {
		_clock = clock;
	}

	private MessageDigest _getMessageDigest() {
		try {
			return MessageDigest.getInstance(_ALGORITHM);
		}
		catch (NoSuchAlgorithmException noSuchAlgorithmException) {

			// Every conformant JVM ships SHA-256, so this cannot happen and
			// swallowing it would hide a broken runtime rather than a missing
			// hash.

			throw new IllegalStateException(noSuchAlgorithmException);
		}
	}

	private byte[] _getSalt(long companyId, long rotationMillis) {
		long now = _clock.millis();

		Salt salt = _salts.compute(
			companyId,
			(key, currentSalt) -> {
				if ((currentSalt == null) ||
					((now - currentSalt.createTime) >= rotationMillis)) {

					return new Salt(_generateSalt(), now);
				}

				return currentSalt;
			});

		return salt.value;
	}

	private byte[] _generateSalt() {
		byte[] salt = new byte[_SALT_LENGTH];

		_secureRandom.nextBytes(salt);

		return salt;
	}

	private static final String _ALGORITHM = "SHA-256";

	private static final int _SALT_LENGTH = 32;

	private Clock _clock = Clock.systemUTC();
	private final Map<Long, Salt> _salts = new ConcurrentHashMap<>();
	private final SecureRandom _secureRandom = new SecureRandom();

	private static class Salt {

		private Salt(byte[] value, long createTime) {
			this.value = value;
			this.createTime = createTime;
		}

		private final long createTime;
		private final byte[] value;

	}

}
