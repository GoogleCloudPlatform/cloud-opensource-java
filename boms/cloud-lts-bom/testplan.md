Basic approach:

1. Iterate through dependencies in LTS BOM dependencyManagement section.
(Not as trivial as it sounds because of variables and included boms.
However dashboard should already have code to do this.
Alternately start with a manually curated flat list of the dependencies.)

2. For each project in the bom:

  a) load the test jar. (for now skip and log if there's no test jar. Longer 
     term, note these and ask the relevant team to publish the test jar.)
  b) Create a pom.xml to run the test jar. Use `mvn dependency:list` to
     find the relevant dependencies.  Replace any versions found in the 
     LTS BOM with the LTS version.
  c) Run `mvn test` on the project and log any failures.
  d) We'll probably need an exclusion list of tests that require too much setup;
     e.g. ITStorageTest in google-cloud-storage
  
  
For now we'll shell out and run a Process to execute Maven. If it's
useful, we can replace this with direct Java invocations of the Maven API.