<html lang="en-US">
  <head>
    <title>Google Cloud Platform Code Health Open Source Dashboard</title>
    <style>
    .PASS {
      background-color: green;
      font-weight: bold;
    }
    .FAIL {
      background-color: red;
      font-weight: bold;
    }
    .UNAVAILABLE {
      background-color: gray;
      font-weight: bold;
    }
    
    body {
      font-family: "Poppins", sans-serif;
      font-weight: 400;
      font-size: 16px;
      line-height: 1.625;
    }

    h1,
    h2,
    h3 {
      color: #333333;
      font-weight: 700;
      margin: 0;
      line-height: 1.2;
    }
    
    h1 {
      font-size: 36pt;
    }
    
    h2 {
      font-size: 30pt;
    }
    
    h3 {
      font-size: 24pt;
    }
    th, td {
      padding: 5pt;
    }
    </style>
  </head>
  <body>
    <h1>Google Cloud Platform Dependency Dashboard</h1>
    <h2>Artifact Details</h2>
    <table>
      <tr>
        <th>Artifact</th><th>Upper Bounds</th><th>Dependency Convergence</th><th>Global Upper Bounds</th>
      </tr>
      <#list table as row>
        <!-- TODO use a macro to remove duplicate code for each td --> 
        <tr>
          <td><a href='${row.getCoordinates()?replace(":", "_")}.html'>${row.getCoordinates()}</a></td>
          <#if row.getResult("Upper Bounds")?? ><#-- checking isNotNull() -->
            <#-- When it's not null, it means the test ran. It's either PASS or FAIL -->
            <#assign upper_bound_test_label = row.getResult("Upper Bounds")?then('PASS', 'FAIL')>
            <#assign upper_bound_failure_count = row.getFailureCount("Upper Bounds")>
          <#else>
            <#-- Null means there's an exception and test couldn't run -->
            <#assign upper_bound_test_label = "UNAVAILABLE">
          </#if>
          <td class='${upper_bound_test_label}' title="${row.getExceptionMessage()!""}">
            <#if row.getResult("Upper Bounds")?? >
              <#if upper_bound_failure_count == 1>1 FAILURE
              <#elseif upper_bound_failure_count gt 1>${upper_bound_failure_count} FAILURES
              <#else>PASS
              </#if>
            <#else>UNAVAILABLE
            </#if>
          </td>
          <#if row.getResult("Dependency Convergence")?? >
            <#assign dependency_convergence_test_label = row.getResult("Dependency Convergence")?then('PASS', 'FAIL') >
            <#assign dependency_convergence_failure_count = row.getFailureCount("Dependency Convergence")>
          <#else>
            <#assign dependency_convergence_test_label = "UNAVAILABLE">
          </#if>
          <td class='${dependency_convergence_test_label}' title="${row.getExceptionMessage()!""}">
            <#if row.getResult("Dependency Convergence")?? >
              <#if dependency_convergence_failure_count == 1>1 FAILURE
              <#elseif dependency_convergence_failure_count gt 1>${dependency_convergence_failure_count} FAILURES
              <#else>PASS
              </#if>
            <#else>UNAVAILABLE
            </#if>
          </td>
          <#if row.getResult("Global Upper Bounds")?? ><#-- checking isNotNull() -->
            <#-- When it's not null, it means the test ran. It's either PASS or FAIL -->
            <#assign global_upper_bound_test_label = row.getResult("Global Upper Bounds")?then('PASS', 'FAIL')>
            <#assign global_upper_bound_failure_count = row.getFailureCount("Global Upper Bounds")>
          <#else>
            <#-- Null means there's an exception and test couldn't run -->
            <#assign upper_bound_test_label = "UNAVAILABLE">
          </#if>
          <td class='${global_upper_bound_test_label}' title="${row.getExceptionMessage()!""}">
            <#if row.getResult("Upper Bounds")?? >
              <#if global_upper_bound_failure_count == 1>1 FAILURE
              <#elseif global_upper_bound_failure_count gt 1>${global_upper_bound_failure_count} FAILURES
              <#else>PASS
              </#if>
            <#else>UNAVAILABLE
            </#if>
          </td>
        </tr>
      </#list>
    </table>
      
     <h2>Recommended Versions</h2>
      
     <p>These are the most recent versions of dependencies used by any of the covered artifacts.</p> 
      
     <ul>
       <#list latestArtifacts as artifact, version>
         <li>${artifact}:${version}</li>
       </#list>
     </ul>
      
      <hr />
      <p id='updated'>Last generated at ${lastUpdated}</p>
  </body>
</html>