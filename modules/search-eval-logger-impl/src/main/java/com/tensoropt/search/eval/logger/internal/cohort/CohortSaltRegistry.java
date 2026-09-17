/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.internal.cohort;

import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;
import java.security.SecureRandom;

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

		try {
			MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

			messageDigest.update(salt);
			messageDigest.update(
				String.valueOf(userId).getBytes(StandardCharsets.UTF_8));

			return _toHexString(messageDigest.digest());
		}
		catch (Exception exception) {
			return null;
		}
	}

	private byte[] _getSalt(long companyId, long rotationMillis) {
		long now = System.currentTimeMillis();

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

	private String _toHexString(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);

		for (byte b : bytes) {
			sb.append(Character.forDigit((b >> 4) & 0xf, 16));
			sb.append(Character.forDigit(b & 0xf, 16));
		}

		return sb.toString();
	}

	private static final int _SALT_LENGTH = 32;

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
