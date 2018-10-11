<html lang="en-US">
  <head>
    <title>Google Cloud Platform Code Health Open Source Dashboard</title>
    <style>
    .PASS {
      background-color: green;
      font-weight: bold;
    }
    .FAILURES {
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
    </style>
  </head>
  <body>
    <h1>Google Cloud Platform Dependency Dashboard</h1>
    <h2>Artifact Details</h2>
    <table>
      <tr>
        <th>Artifact</th><th>Upper Bounds</th><th>Dependency Convergence</th>
      </tr>
      <#list table as row>
        <tr>
          <#if row.getResult("Upper Bounds")?? ><#-- checking isNotNull() -->
            <#-- When it's not null, it means the test ran. It's either PASS or FAIL -->
            <#assign upper_bound_test_label = row.getResult("Upper Bounds")?then('PASS', 'FAILURES')>
            <#assign upper_bound_failure_count = row.getFailureCount("Upper Bounds")>
          <#else>
            <#-- Null means there's an exception and test couldn't run -->
            <#assign upper_bound_test_label = "UNAVAILABLE">
          </#if>
          <td><a href='${row.getCoordinates()?replace(":", "_")}.html'>${row.getCoordinates()}</a></td>
          <td class='${upper_bound_test_label}' title="${row.getExceptionMessage()!""}">
            <#if upper_bound_failure_count gt 0> ${upper_bound_failure_count}</#if> ${upper_bound_test_label}
          </td>
          <#if row.getResult("Dependency Convergence")?? >
            <#assign dependency_convergence_test_label = row.getResult("Dependency Convergence")?then('PASS', 'FAILURES') >
            <#assign dependency_convergence_failure_count = row.getFailureCount("Dependency Convergence")>
          <#else>
            <#assign dependency_convergence_test_label = "UNAVAILABLE">
          </#if>
          <td class='${dependency_convergence_test_label}' title="${row.getExceptionMessage()!""}">
            <#if dependency_convergence_failure_count gt 0>
            ${dependency_convergence_failure_count} </#if>${dependency_convergence_test_label} 
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