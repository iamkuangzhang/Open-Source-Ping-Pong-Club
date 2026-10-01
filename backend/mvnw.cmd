@ECHO OFF
SETLOCAL
SET "WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar"
IF NOT EXIST "%WRAPPER_JAR%" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://repo.maven.apache.org/maven2/io/takari/maven-wrapper/0.5.6/maven-wrapper-0.5.6.jar' -OutFile '%WRAPPER_JAR%'"
)
java -Dmaven.multiModuleProjectDirectory="%CD%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
SET "WRAPPER_EXIT_CODE=%ERRORLEVEL%"
ENDLOCAL & EXIT /B %WRAPPER_EXIT_CODE%
