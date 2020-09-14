@echo on

set JAVA_HOME=c:\program files\java\jdk1.8.0_152
set PATH=%JAVA_HOME%\bin;%PATH%

cd github/cloud-opensource-java

mkdir %USERPROFILE%\.m2
copy settings.xml %USERPROFILE%\.m2

call mvnw.cmd -V -B clean install javadoc:jar

if %errorlevel% neq 0 exit /b %errorlevel%
@echo on

cd gradle-plugin
call gradlew.bat build publishToMavenLocal

exit /b %ERRORLEVEL%
