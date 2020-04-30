def buildLog = new File(basedir, "build.log").text.replaceAll("\\r\\n", "\\n")

// Windows build contains \r character
assert buildLog.contains('''\
[ERROR] Linkage Checker rule found 16 errors. Linkage error report:
Class javax.jms.Connection is not found;
  referenced by 1 class file
    org.apache.log4j.net.JMSAppender (log4j:log4j:1.2.12)
''')

assert buildLog.contains('''\
[ERROR] Problematic artifacts in the dependency tree:
log4j:log4j:1.2.12 is at:
  com.google.cloud.tools.opensource:test-no-such-method-error-example:jar:1.0-SNAPSHOT \
/ com.google.api-client:google-api-client:1.27.0 (compile) \
/ com.google.oauth-client:google-oauth-client:1.27.0 (compile) \
/ com.google.http-client:google-http-client:1.27.0 (compile) \
/ com.google.android:android:1.5_r4 (provided) \
/ commons-logging:commons-logging:1.1.1 (compile) \
/ log4j:log4j:1.2.12 (compile, optional)
''')
