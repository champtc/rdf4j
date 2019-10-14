node {

	def err = null
	def buildVersion = "UNKNOWN"
	currentBuild.result = "SUCCESS"
	def buildDisplayName = "${env.JOB_NAME}".replaceAll('%2F','/');

	try {

		def branchname = "${env.BRANCH_NAME}"
		if (branchname == null || branchname == "null") {
			branchname = "test-build"
		}
		branchname = "${branchname}".replaceAll("/","-")
		def versionState = "${branchname}".replaceAll("\\.","_")
		
		echo "Building: ${buildDisplayName}"
		milestone()
		
		lock(resource: "rdf4j-${branchname}", inversePrecedence: true) {
		
			def workspaceFolder = "${env.WS_FOLDER}/rdf4j/${branchname}"
			echo "Workspace Folder: ${workspaceFolder}"
			def anthome = tool 'Ant_1.10'
			
			def mvnhome
			if (isUnix()) {
				mvnhome = tool 'Maven_3.6'
			} else {
				// Due to a bug, use Maven 3.3 for Windows builds
				mvnhome = tool 'Maven_3.3'
			}
			
			def workingFolder
			def eclipsehome = "${env.ECLIPSE_4_7}"
			def skipJarSign = "${env.SKIP_JAR_SIGN}"
			
			def propFolder
			def commonProps
			def version
			def scriptState
			def versionProps
			
			def pubServer = "${env.PUB_SERVER}"
			def webServerURL = "${env.WEB_SERVER_URL}"			
			
			ws("${workspaceFolder}") {
							
				stage('Checkout') {			
					checkout scm
				}
				
				stage('Setup Build') {
					env.JAVA_HOME = tool 'JDK_1.8'
					env.WORKSPACE = pwd()
					workingFolder = pwd()
					
					properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '90', artifactNumToKeepStr: '2', daysToKeepStr: '', numToKeepStr: '']]])	
				}
				
				stage('Build RDF4J') {
					if (isUnix()) {
						sh "cd ${workingFolder}\n${mvnhome}/bin/mvn clean install -DskipTests=true"
					} else {
						bat "cd ${workingFolder}\n${mvnhome}\\bin\\mvn clean install -DskipTests=true"
					}
				}
				
				stage('Record Results') {
					recordIssues enabledForFailure: true, tools: [eclipse(), taskScanner(highTags: 'FIXME', ignoreCase: true, includePattern: '**/plugins/**/*.java,', lowTags: 'XXX', normalTags: 'TODO')]
				}
				
				//stage('Build DarkLight Core') {
				//	try {
				//		build job: "thirdparty/lib/develop", quietPeriod: 15, wait: false
				//	} catch (Exception ex) {
				//		echo "Failed to trigger darklight build: thirdparty/lib/develop"
				//	}
				//	
				//	try {
				//		build job: "thirdparty/lib/master", quietPeriod: 15, wait: false
				//	} catch (Exception ex) {
				//		echo "Failed to trigger darklight build: thirdparty/lib/master"
				//	}
				//}				
			}
			milestone()
		}
			
	} catch (Exception ex) {
		err = ex
		currentBuild.result = "FAILURE"	 
		
	} finally {	
		step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: '', sendToIndividuals: true])
		
		if (err != null) {
			throw err;
		}
	}
	
}