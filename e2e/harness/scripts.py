# SPDX-License-Identifier: Apache-2.0
"""The Groovy fragments the harness runs through the script console.

They are here, in one file, for the same reason EvaluationServiceLinks keeps
both URLs in one class: anyone asking "what does this harness do inside the
portal" should have one place to read.

Three of them reach OSGi services by name and call them reflectively. That is
deliberate. search-eval-logger-impl exports nothing, so its purge and its daily
cycle check are not on any classpath a script can import; the alternative was
to export them, or to add a trigger to the plugin that exists only for tests.
Reflection keeps the production bundle exactly as it ships, and the code being
called is reached the same way the scheduler reaches it.
"""

COMPANY_ID = """
result = String.valueOf(
    com.liferay.portal.kernel.util.PortalUtil.getDefaultCompanyId())
"""

GROUP_ID = """
long companyId = %(company_id)dL
result = String.valueOf(
    com.liferay.portal.kernel.service.GroupLocalServiceUtil.getGroup(
        companyId, "%(group_key)s").getGroupId())
"""

# SystemBundleUtil rather than FrameworkUtil.getBundle(SomeClass.class).
# portal-kernel is loaded by the shielded container, not by a bundle, so
# FrameworkUtil.getBundle on any kernel class returns null and every call on it
# fails with a NullPointerException that says nothing about why.
_SERVICE_PREAMBLE = """
def __bundleContext =
    com.liferay.portal.kernel.module.util.SystemBundleUtil.getBundleContext()

def __find = { String name ->
    def refs = __bundleContext.getServiceReferences(name, null)

    if (refs == null || refs.length == 0) {
        throw new IllegalStateException("No service registered under " + name)
    }

    return __bundleContext.getService(refs[0])
}

def __call = { Object service, String methodName, Class[] types, Object[] args ->
    def method = service.getClass().getMethod(methodName, types)

    method.setAccessible(true)

    return method.invoke(service, args)
}
"""

# Runs the retention purge for one virtual instance, now.
#
# The purge is a daily SchedulerJobConfiguration and a CI run cannot wait a
# day. This calls the same RetentionPurger the scheduler calls, with the same
# argument, so what is exercised is the production path and not a shortcut
# around it.
PURGE = _SERVICE_PREAMBLE + """
def purger = __find("ai.tensoropt.sel.internal.purge.RetentionPurger")

long started = System.currentTimeMillis()

__call(purger, "purge", [long.class] as Class[], [%(company_id)dL] as Object[])

result = String.valueOf(System.currentTimeMillis() - started)
"""

# Runs the daily collection cycle check of DESIGN.md 3.6, now.
CYCLE_CHECK = _SERVICE_PREAMBLE + """
def monitor = __find("ai.tensoropt.sel.internal.cycle.CollectionCycleMonitor")

long started = System.currentTimeMillis()

__call(monitor, "check", [long.class] as Class[], [%(company_id)dL] as Object[])

result = String.valueOf(System.currentTimeMillis() - started)
"""

# Asks the plugin's own detector whether searches are reaching the wrapper.
INTERCEPTION_STATUS = _SERVICE_PREAMBLE + """
def status = __find("ai.tensoropt.sel.api.SearchInterceptionStatus")

result = String.valueOf(
    __call(status, "isIntercepting", new Class[0], new Object[0]))
"""

# Which Searcher services are registered, and which bundles are using each.
# This is the raw evidence behind EC-3: a non-wrapper Searcher with a consumer
# bundle other than the plugin's own is a consumer that will never rebind.
SEARCHER_REGISTRY = """
def bundleContext =
    com.liferay.portal.kernel.module.util.SystemBundleUtil.getBundleContext()

def refs = bundleContext.getServiceReferences(
    "com.liferay.portal.search.searcher.Searcher", null)

def lines = []

if (refs != null) {
    for (ref in refs) {
        def users = []

        def usingBundles = ref.getUsingBundles()

        if (usingBundles != null) {
            for (bundle in usingBundles) {
                users.add(String.valueOf(bundle.getSymbolicName()))
            }
        }

        lines.add(
            String.valueOf(ref.getProperty("component.name")) + " ranking=" +
                String.valueOf(ref.getProperty("service.ranking")) +
                    " marker=" +
                        String.valueOf(ref.getProperty("search.eval.logger")) +
                            " users=" + users.join(","))
    }
}

result = lines.join("\\n")
"""

# The collection cycle record of DESIGN.md 3.6, read from where the plugin
# keeps it: company scoped portlet preferences under its own portlet id.
# The plugin's two scheduled jobs, with their next two fire times in UTC.
#
# Read off each job's own trigger, so what is reported is what the scheduler
# will actually do rather than what the configuration class asks for.
SCHEDULED_JOBS = """
def format = { date ->
    date == null ? null :
        java.time.format.DateTimeFormatter.ISO_INSTANT.format(date.toInstant())
}
def jobs = []
com.liferay.portal.kernel.scheduler.StorageType.values().each { storageType ->
    com.liferay.portal.kernel.scheduler.SchedulerEngineHelperUtil.getScheduledJobs(
            storageType).each { response ->
        if (!response.getJobName().startsWith("ai.tensoropt.sel.")) return
        def trigger = response.getTrigger()
        def next = trigger.getFireDateAfter(new Date())
        jobs << [job: response.getJobName(), next: format(next),
                 following: format(next == null ? null : trigger.getFireDateAfter(next))]
    }
}
result = JsonOutput.toJson(jobs)
"""

CYCLE_READ = """
long companyId = %(company_id)dL

def preferences =
    com.liferay.portal.kernel.service.PortletPreferencesLocalServiceUtil.
        getPreferences(
            companyId, companyId,
            com.liferay.portal.kernel.util.PortletKeys.PREFS_OWNER_TYPE_COMPANY,
            com.liferay.portal.kernel.util.PortletKeys.PREFS_PLID_SHARED,
            "%(portlet_id)s")

def values = [:]

for (key in ["open", "enabledByUserId", "collectionStartDate",
             "readinessNotifiedDate", "stallNotifiedDate"]) {

    values.put(key, String.valueOf(preferences.getValue(key, "")))
}

result = values
"""

# Moves the collection start date back, so the readiness condition can be
# evaluated against its real thresholds instead of thresholds lowered to suit
# the test. It writes the same preference the plugin writes, through the same
# service, so nothing about the record's shape is assumed.
CYCLE_BACKDATE = """
long companyId = %(company_id)dL

def preferences =
    com.liferay.portal.kernel.service.PortletPreferencesLocalServiceUtil.
        getPreferences(
            companyId, companyId,
            com.liferay.portal.kernel.util.PortletKeys.PREFS_OWNER_TYPE_COMPANY,
            com.liferay.portal.kernel.util.PortletKeys.PREFS_PLID_SHARED,
            "%(portlet_id)s")

preferences.setValue("collectionStartDate", "%(collection_start_date)s")

com.liferay.portal.kernel.service.PortletPreferencesLocalServiceUtil.
    updatePreferences(
        companyId,
        com.liferay.portal.kernel.util.PortletKeys.PREFS_OWNER_TYPE_COMPANY,
        com.liferay.portal.kernel.util.PortletKeys.PREFS_PLID_SHARED,
        "%(portlet_id)s", preferences)

result = "%(collection_start_date)s"
"""

# Lets an ordinary signed in user open the plugin's screen while leaving EXPORT
# ungranted, which is the only way to exercise the refusal path: a user who
# cannot open the screen at all is stopped by portlet access, not by the
# dedicated permission DESIGN.md 6.1 describes.
GRANT_SCREEN_ACCESS_TO_USER_ROLE = """
long companyId = %(company_id)dL

def role = com.liferay.portal.kernel.service.RoleLocalServiceUtil.getRole(
    companyId, "User")

com.liferay.portal.kernel.service.ResourcePermissionLocalServiceUtil.
    setResourcePermissions(
        companyId, "%(portlet_id)s",
        com.liferay.portal.kernel.model.ResourceConstants.SCOPE_COMPANY,
        String.valueOf(companyId), role.getRoleId(),
        ["ACCESS_IN_CONTROL_PANEL", "VIEW"] as String[])

result = String.valueOf(role.getRoleId())
"""


# Clears the gates a freshly created user meets on first sign in. A user made
# through the headless API has an unverified email address, and Liferay sends
# them to a verification screen instead of to the page they asked for, so
# without this the permission test would read "no export form" as "refused"
# when the real answer is "never got there".
ACTIVATE_USER = """
long companyId = %(company_id)dL

def userLocalService =
    com.liferay.portal.kernel.service.UserLocalServiceUtil

def user = userLocalService.getUserByEmailAddress(companyId, "%(email)s")

userLocalService.updateEmailAddressVerified(user.getUserId(), true)
userLocalService.updatePasswordReset(user.getUserId(), false)
userLocalService.updateAgreedToTermsOfUse(user.getUserId(), true)

result = String.valueOf(user.getUserId())
"""
