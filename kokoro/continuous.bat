@echo on

set JAVA_HOME=c:\program files\java\jdk1.8.0_152
set PATH=%JAVA_HOME%\bin;%PATH%

cd github/cloud-opensource-java

mkdir %USERPROFILE%\.m2
copy settings.xml %USERPROFILE%\.m2

mvnw.cmd -B clean install javadoc:jar -DskipTests

exit /b %ERRORLEVEL%
