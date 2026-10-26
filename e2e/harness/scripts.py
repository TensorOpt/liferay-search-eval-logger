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

USER_ID_BY_EMAIL = """
long companyId = %(company_id)dL
result = String.valueOf(
    com.liferay.portal.kernel.service.UserLocalServiceUtil.
        getUserByEmailAddress(companyId, "%(email)s").getUserId())
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

# The search widgets that are deployed and can render right now: active, with
# a portlet bag. Taking the wrapper away makes Declarative Services deactivate
# and reactivate every widget holding a static reference down to Searcher, and
# for a moment most of them are gone (17 dropping to 2 on 2025.Q1.27), ending
# after the last bundle has already logged STOPPED. Rendering the search page
# inside that moment answers without the Search Results widget, or HTTP 400
# with "No portlet bag" logged at ERROR. This reads the same state without
# rendering anything.
SEARCH_WIDGETS_DEPLOYED = """
def deployed = com.liferay.portal.kernel.service.PortletLocalServiceUtil.
    getPortlets(%(company_id)dL).findAll {
        it.getPortletId().startsWith("com_liferay_portal_search_web") &&
            it.isActive() &&
            com.liferay.portal.kernel.portlet.PortletBagPool.get(
                it.getRootPortletId()) != null
    }

result = JsonOutput.toJson(deployed*.getPortletId().sort())
"""

# Empties the zero-result panel's one hour cache (TO-135), as Server
# Administration's "Clear content cached by this VM" would, so the next render
# of the export screen recomputes the panel from the log.
FLUSH_ZERO_RESULT_PANEL_CACHE = """
def bundleContext =
    com.liferay.portal.kernel.module.util.SystemBundleUtil.getBundleContext()

def reference = bundleContext.getServiceReference(
    com.liferay.portal.kernel.cache.SingleVMPool)

bundleContext.getService(reference).getPortalCache(
    "ai.tensoropt.sel.web.internal.zeroresult.ZeroResultPanel").removeAll()

result = "flushed"
"""

# The plugin's bundles as the framework sees them: state (32 is ACTIVE) and
# location, which says whether a bundle came from a jar or from inside an .lpkg.
PLUGIN_BUNDLES = """
def bundles = com.liferay.portal.kernel.module.util.SystemBundleUtil.getBundleContext().getBundles()

result = JsonOutput.toJson(
    bundles.findAll { it.getSymbolicName()?.startsWith("ai.tensoropt.sel.") }.collect {
        [name: it.getSymbolicName(), state: it.getState(), location: it.getLocation()]
    })
"""

# TO-140: forces the genuine bypass ec3-bypass-detected needs, using only the
# OSGi runtime, no restart. SearchConsumerRebinder is disabled first so it
# cannot immediately undo the rest of this: with it still active, bouncing
# LoggingSearcher would just retrigger the same automatic rebind that
# install-intercepts-without-restart already proved works. Bouncing
# LoggingSearcher itself then unregisters the wrapper for a moment, which
# unbinds the entry point components' static reference and lets it rebind, on
# the only Searcher left, the portal's own; re-enabling LoggingSearcher brings
# the wrapper back, but a static reluctant reference already bound and
# satisfied does not move for it, which is exactly EC-3's bypass.
FORCE_SEARCH_CONSUMER_BYPASS = """
def bundleContext =
    com.liferay.portal.kernel.module.util.SystemBundleUtil.getBundleContext()

def reference = bundleContext.getServiceReference(
    org.osgi.service.component.runtime.ServiceComponentRuntime)

def scr = bundleContext.getService(reference)

def find = { String name ->
    scr.getComponentDescriptionDTOs().find { it.name == name }
}

def rebinder = find("ai.tensoropt.sel.internal.searcher.SearchConsumerRebinder")

scr.disableComponent(rebinder).getValue()

def loggingSearcher = find("ai.tensoropt.sel.internal.searcher.LoggingSearcher")

scr.disableComponent(loggingSearcher).getValue()
scr.enableComponent(loggingSearcher).getValue()

result = "forced"
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

# TO-144: the OSGi JAX-RS whiteboard's own view of its health, read through
# the standard org.osgi.service.jaxrs.runtime.JaxrsServiceRuntime service
# (OSGi Compendium 151), not through anything this plugin owns. Used as the
# positive recovery check around uninstall_and_reinstall: UNINSTALL_TOLERATED
# accepts the vulcan/Cxf rewire errors that cascade sets off (DESIGN.md 3.1),
# and this is what proves the whiteboard actually settles back down rather
# than the tolerance rule silently hiding a portal left broken.
JAXRS_WHITEBOARD_HEALTH = """
def bundleContext =
    com.liferay.portal.kernel.module.util.SystemBundleUtil.getBundleContext()

def reference = bundleContext.getServiceReference(
    org.osgi.service.jaxrs.runtime.JaxrsServiceRuntime)

def dto = bundleContext.getService(reference).getRuntimeDTO()

result = JsonOutput.toJson([
    failedApplications: dto.failedApplicationDTOs.length,
    failedResources: dto.failedResourceDTOs.length,
    failedExtensions: dto.failedExtensionDTOs.length])
"""

# Closes and reopens the collection cycle, through the same service the
# configuration listener and the daily job call (TO-112's re-arm test):
# CollectionCycleStatusImpl.closeCycle then .openCycle. Reflective for the
# same reason PURGE and CYCLE_CHECK are: search-eval-logger-impl exports
# nothing.
CYCLE_REOPEN = _SERVICE_PREAMBLE + """
def status = __find("ai.tensoropt.sel.internal.cycle.CollectionCycleStatusImpl")

__call(
    status, "closeCycle", [long.class] as Class[], [%(company_id)dL] as Object[])
__call(
    status, "openCycle", [long.class, long.class] as Class[],
    [%(company_id)dL, %(enabled_by_user_id)dL] as Object[])

result = "ok"
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

# A background task of some other kind, with an attachment, in the same
# company and site as the exports (TO-91, TO-109). The executor class name
# names nothing, so the task is never run; the attachment is all that matters.
FOREIGN_TASK = """
long companyId = %(company_id)dL
long groupId = %(group_id)dL
def user = com.liferay.portal.kernel.service.UserLocalServiceUtil.getUserByEmailAddress(
    companyId, "%(email)s")
def serviceContext = new com.liferay.portal.kernel.service.ServiceContext()
serviceContext.setCompanyId(companyId)
serviceContext.setScopeGroupId(groupId)
serviceContext.setUserId(user.getUserId())
def manager = com.liferay.portal.kernel.backgroundtask.BackgroundTaskManagerUtil
def task = manager.addBackgroundTask(
    user.getUserId(), groupId, "e2e-foreign-task",
    "com.example.e2e.NotASearchEvalExport", new HashMap(), serviceContext)
def file = File.createTempFile("foreign", ".lar")
file.text = "%(content)s"
manager.addBackgroundTaskAttachment(
    user.getUserId(), task.getBackgroundTaskId(), "foreign.lar", file)
result = JsonOutput.toJson([
    id: task.getBackgroundTaskId(),
    attachments: manager.getBackgroundTask(
        task.getBackgroundTaskId()).getAttachmentsFileEntriesCount()])
"""

# EC-15 (TO-112): the isDeliver gate, checked directly against a running
# instance rather than only through CollectionNotifier's own behaviour.
# UserNotificationManagerUtil is public portal-kernel API, so this needs no
# reflection the way CYCLE_CHECK and PURGE do.
EMAIL_DELIVERY_PREFERENCE = """
long userId = %(user_id)dL

def definition =
    com.liferay.portal.kernel.notifications.UserNotificationManagerUtil.
        fetchUserNotificationDefinition("%(portlet_id)s", 0L, 0)

def deliverEmail =
    com.liferay.portal.kernel.notifications.UserNotificationManagerUtil.
        isDeliver(userId, "%(portlet_id)s", 0L, 0, 10000)

result = JsonOutput.toJson([
    definitionFound: definition != null,
    isDeliverEmail: deliverEmail])
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

for (key in ["open", "enabledByUserId", "cycleOpenedDate",
             "collectionStartDate", "collectionStartedNotifiedDate",
             "readinessNotifiedDate", "stallNotifiedDate",
             "signupBannerDismissed"]) {

    values.put(key, String.valueOf(preferences.getValue(key, "")))
}

result = values
"""

# Writes arbitrary preference values into the collection cycle record, through
# the same service the plugin itself writes with. Used to backdate
# collectionStartDate (readiness) and cycleOpenedDate (the TO-112 stall grace
# period) to their real thresholds instead of lowering the thresholds to suit
# the test.
CYCLE_WRITE = """
long companyId = %(company_id)dL

def preferences =
    com.liferay.portal.kernel.service.PortletPreferencesLocalServiceUtil.
        getPreferences(
            companyId, companyId,
            com.liferay.portal.kernel.util.PortletKeys.PREFS_OWNER_TYPE_COMPANY,
            com.liferay.portal.kernel.util.PortletKeys.PREFS_PLID_SHARED,
            "%(portlet_id)s")

%(assignments)s

com.liferay.portal.kernel.service.PortletPreferencesLocalServiceUtil.
    updatePreferences(
        companyId,
        com.liferay.portal.kernel.util.PortletKeys.PREFS_OWNER_TYPE_COMPANY,
        com.liferay.portal.kernel.util.PortletKeys.PREFS_PLID_SHARED,
        "%(portlet_id)s", preferences)

result = "ok"
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
# D8: flips the company-scoped @Meta.OCD configuration's "enabled" flag
# (DESIGN.md 5) for the search-overhead case, through the same
# ConfigurationProvider.getCompanyConfiguration/saveCompanyConfiguration pair
# SearchEvalLoggerConfigurationRegistry and the export background task use.
# Every other attribute is read off the live, typed configuration first and
# written straight back unchanged, so the round trip changes exactly the one
# key the case means to change, never resetting captureDepth, retentionDays
# or anything else to its declared default. The configuration interface is
# loaded by name from the api bundle's own class loader, the same reason
# __find looks services up by name: a Groovy script's own class loader does
# not see plugin classes, only portal-kernel and the OSGi framework. Returns
# the value "enabled" held before this call, so the caller can restore it.
SET_CONFIGURATION_ENABLED = _SERVICE_PREAMBLE + """
long companyId = %(company_id)dL
boolean enabled = %(enabled)s

def configurationClass = __bundleContext.getBundles().find {
    it.getSymbolicName() == "ai.tensoropt.sel.api"
}.loadClass("ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration")

def configurationProvider = __find(
    "com.liferay.portal.configuration.module.configuration.ConfigurationProvider")

def current = __call(
    configurationProvider, "getCompanyConfiguration",
    [Class.class, long.class] as Class[],
    [configurationClass, companyId] as Object[])

def properties = new Hashtable()
def previousEnabled = null

for (method in configurationClass.getMethods()) {
    if (method.getParameterCount() != 0) continue

    def name = method.getName()

    if (name in ["equals", "hashCode", "toString", "annotationType"]) continue

    def value = method.invoke(current)

    if (name == "enabled") previousEnabled = value

    if (value != null) properties.put(name, value)
}

properties.put("enabled", enabled)

__call(
    configurationProvider, "saveCompanyConfiguration",
    [Class.class, long.class, Dictionary.class] as Class[],
    [configurationClass, companyId, properties] as Object[])

result = String.valueOf(previousEnabled)
"""

# D8: the read-only half of SET_CONFIGURATION_ENABLED, polled after a toggle
# so the case waits for the change to actually take effect rather than
# assuming ConfigurationAdmin's write is visible the instant the call above
# returns.
CONFIGURATION_ENABLED = _SERVICE_PREAMBLE + """
long companyId = %(company_id)dL

def configurationClass = __bundleContext.getBundles().find {
    it.getSymbolicName() == "ai.tensoropt.sel.api"
}.loadClass("ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration")

def configurationProvider = __find(
    "com.liferay.portal.configuration.module.configuration.ConfigurationProvider")

def current = __call(
    configurationProvider, "getCompanyConfiguration",
    [Class.class, long.class] as Class[],
    [configurationClass, companyId] as Object[])

def enabledMethod = configurationClass.getMethod("enabled")

result = String.valueOf(enabledMethod.invoke(current))
"""

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
