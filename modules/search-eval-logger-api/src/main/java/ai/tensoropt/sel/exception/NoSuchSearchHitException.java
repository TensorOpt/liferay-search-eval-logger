/**
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.tensoropt.sel.exception;

import com.liferay.portal.kernel.exception.NoSuchModelException;

/**
 * @author Brian Wing Shun Chan
 */
public class NoSuchSearchHitException extends NoSuchModelException {

	public NoSuchSearchHitException() {
	}

	public NoSuchSearchHitException(String msg) {
		super(msg);
	}

	public NoSuchSearchHitException(String msg, Throwable throwable) {
		super(msg, throwable);
	}

	public NoSuchSearchHitException(Throwable throwable) {
		super(throwable);
	}

}