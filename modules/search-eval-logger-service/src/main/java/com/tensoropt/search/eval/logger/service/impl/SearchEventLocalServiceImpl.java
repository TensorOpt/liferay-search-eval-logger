/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.tensoropt.search.eval.logger.service.impl;

import com.liferay.portal.aop.AopService;

import com.tensoropt.search.eval.logger.service.base.SearchEventLocalServiceBaseImpl;

import org.osgi.service.component.annotations.Component;

/**
 * @author Brian Wing Shun Chan
 */
@Component(
	property = "model.class.name=com.tensoropt.search.eval.logger.model.SearchEvent",
	service = AopService.class
)
public class SearchEventLocalServiceImpl
	extends SearchEventLocalServiceBaseImpl {
}