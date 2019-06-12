import java.util.logging.Logger
import jenkins.security.s2m.*
import hudson.model.*
import hudson.security.*
import jenkins.model.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.jenkins.plugins.sshcredentials.impl.*
import hudson.slaves.*
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry
import hudson.plugins.sshslaves.*
import hudson.plugins.sshslaves.verifiers.*
import hudson.plugins.openid.*

def logger = Logger.getLogger("")
logger.info("Disabling CLI over remoting")
jenkins.CLI.get().setEnabled(false);
logger.info("Enable Slave -> Master Access Control")
Jenkins.instance.injector.getInstance(AdminWhitelistRule.class).setMasterKillSwitch(false)
Jenkins.instance.save()

global_domain = Domain.global()

def loginRealm = new OpenIdSsoSecurityRealm("https://id.fedoraproject.org/")
Jenkins.instance.setSecurityRealm(loginRealm)

def matrix_auth = new ProjectMatrixAuthorizationStrategy()
matrix_auth.add(hudson.model.Hudson.READ,'anonymous')
matrix_auth.add(hudson.model.Item.DISCOVER,'anonymous')
matrix_auth.add(hudson.model.Item.READ,'anonymous')
matrix_auth.add(hudson.model.Hudson.ADMINISTER, 'sysadmin-jenkins')

Jenkins.instance.setAuthorizationStrategy(matrix_auth)
