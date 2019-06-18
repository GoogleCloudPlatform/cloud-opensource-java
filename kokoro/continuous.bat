@echo on

set JAVA_HOME=c:\program files\java\jdk1.8.0_152
set PATH=%JAVA_HOME%\bin;%PATH%

cd github/cloud-opensource-java
mvn -s settings.xml -B clean install javadoc:jar

exit /b %ERRORLEVEL%
