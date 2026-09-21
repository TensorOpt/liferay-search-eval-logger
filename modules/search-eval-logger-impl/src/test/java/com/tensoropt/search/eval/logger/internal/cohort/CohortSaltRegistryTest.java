/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.internal.cohort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The privacy guarantee in DESIGN.md 4.3 is entirely a property of this class:
 * a hash that is stable inside a rotation window and unrelatable outside it.
 * Both halves are tested, which is only possible because the clock is
 * injected rather than read from the system.
 */
public class CohortSaltRegistryTest {

	@BeforeEach
	public void setUp() {
		_cohortSaltRegistry = new CohortSaltRegistry();

		_now = Instant.parse("2026-01-01T00:00:00Z");

		_cohortSaltRegistry.setClock(_tickingClock());
	}

	@Test
	public void hashIsStableForOneUserWithinARotationWindow() {
		String first = _cohortSaltRegistry.hash(1L, 42L, 7);

		_now = _now.plus(Duration.ofDays(6));

		assertEquals(first, _cohortSaltRegistry.hash(1L, 42L, 7));
	}

	@Test
	public void hashChangesWhenTheRotationPeriodElapses() {
		String before = _cohortSaltRegistry.hash(1L, 42L, 7);

		_now = _now.plus(Duration.ofDays(7));

		assertNotEquals(
			before, _cohortSaltRegistry.hash(1L, 42L, 7),
			"A salt older than the rotation period must be replaced, or the " +
				"hash stays linkable for the whole retention window");
	}

	@Test
	public void differentUsersGetDifferentHashesInTheSameWindow() {
		assertNotEquals(
			_cohortSaltRegistry.hash(1L, 42L, 7),
			_cohortSaltRegistry.hash(1L, 43L, 7));
	}

	/**
	 * Cohorts are never compared across virtual instances (4.3), so the salt
	 * is per company and the same user id must not collide between them.
	 */
	@Test
	public void companiesDoNotShareASalt() {
		assertNotEquals(
			_cohortSaltRegistry.hash(1L, 42L, 7),
			_cohortSaltRegistry.hash(2L, 42L, 7));
	}

	@Test
	public void hashIsASha256HexDigest() {
		String hash = _cohortSaltRegistry.hash(1L, 42L, 7);

		assertEquals(
			64, hash.length(),
			"The cohortHash column is sized for exactly this (4.1)");
		assertTrue(hash.matches("[0-9a-f]{64}"));
	}

	/**
	 * A non-positive period would otherwise mean "rotate on every call",
	 * which would make every row its own cohort and destroy the grouping the
	 * field exists for.
	 */
	@Test
	public void nonPositiveRotationPeriodIsTreatedAsOneDay() {
		String first = _cohortSaltRegistry.hash(1L, 42L, 0);

		_now = _now.plus(Duration.ofHours(23));

		assertEquals(first, _cohortSaltRegistry.hash(1L, 42L, 0));

		_now = _now.plus(Duration.ofHours(2));

		assertNotEquals(first, _cohortSaltRegistry.hash(1L, 42L, 0));
	}

	/**
	 * A restart rotates, because the salt is in memory only. That shortens
	 * the linkable span, which 4.3 calls the safe direction of error.
	 */
	@Test
	public void aNewRegistryDoesNotReproduceAnEarlierHash() {
		String before = _cohortSaltRegistry.hash(1L, 42L, 7);

		CohortSaltRegistry restarted = new CohortSaltRegistry();

		restarted.setClock(_tickingClock());

		assertNotEquals(before, restarted.hash(1L, 42L, 7));
	}

	private Clock _tickingClock() {
		return new Clock() {

			@Override
			public ZoneOffset getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Instant instant() {
				return _now;
			}

			@Override
			public Clock withZone(java.time.ZoneId zoneId) {
				return this;
			}

		};
	}

	private CohortSaltRegistry _cohortSaltRegistry;
	private Instant _now;

}
