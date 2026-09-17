/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.tensoropt.search.eval.logger.model;

import com.liferay.portal.kernel.annotation.ImplementationClassName;
import com.liferay.portal.kernel.model.PersistedModel;
import com.liferay.portal.kernel.util.Accessor;

import org.osgi.annotation.versioning.ProviderType;

/**
 * The extended model interface for the SearchHit service. Represents a row in the &quot;SEL_SearchHit&quot; database table, with each column mapped to a property of this class.
 *
 * @author Brian Wing Shun Chan
 * @see SearchHitModel
 * @generated
 */
@ImplementationClassName(
	"com.tensoropt.search.eval.logger.model.impl.SearchHitImpl"
)
@ProviderType
public interface SearchHit extends PersistedModel, SearchHitModel {

	/*
	 * NOTE FOR DEVELOPERS:
	 *
	 * Never modify this interface directly. Add methods to <code>com.tensoropt.search.eval.logger.model.impl.SearchHitImpl</code> and rerun ServiceBuilder to automatically copy the method declarations to this interface.
	 */
	public static final Accessor<SearchHit, Long> SEARCH_HIT_ID_ACCESSOR =
		new Accessor<SearchHit, Long>() {

			@Override
			public Long get(SearchHit searchHit) {
				return searchHit.getSearchHitId();
			}

			@Override
			public Class<Long> getAttributeClass() {
				return Long.class;
			}

			@Override
			public Class<SearchHit> getTypeClass() {
				return SearchHit.class;
			}

		};

}