/**
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.tensoropt.sel.exception;

import com.liferay.portal.kernel.exception.NoSuchModelException;

/**
 * @author Brian Wing Shun Chan
 */
public class NoSuchSearchEventException extends NoSuchModelException {

	public NoSuchSearchEventException() {
	}

	public NoSuchSearchEventException(String msg) {
		super(msg);
	}

	public NoSuchSearchEventException(String msg, Throwable throwable) {
		super(msg, throwable);
	}

	public NoSuchSearchEventException(Throwable throwable) {
		super(throwable);
	}

}