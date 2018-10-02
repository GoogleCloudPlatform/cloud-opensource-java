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
    </style>
  </head>
  <body>
    <h1>Google Cloud Platform Code Health Open Source Dashboard</h1>
    <h2>Projects</h2>
      <table>
      <tr>
        <th>Artifact</th><th>Upper Bounds</th><th>Dependency Convergence</th>
      </tr>
      <#list table as row>
        <tr>
          <#if row.getResult("Upper Bounds")?? ><#-- checking isNotNull() -->
            <#-- When it's not null, it means the test ran. It's either PASS or FAIL -->
            <#assign upper_bound_test_label = row.getResult("Upper Bounds")?then('PASS', 'FAIL') >
          <#else>
            <#-- Null means there's an exception and test couldn't run -->
            <#assign upper_bound_test_label = "UNAVAILABLE">
          </#if>
          <td><a href='${row.getCoordinates()?replace(":", "_")}.html'>${row.getCoordinates()}</a></td>
          <td class='${upper_bound_test_label}' title="${row.getExceptionMessage()!""}">${upper_bound_test_label}</td>

          <#if row.getResult("Dependency Convergence")?? >
            <#assign dependency_convergence_test_label = row.getResult("Dependency Convergence")?then('PASS', 'FAIL') >
          <#else>
            <#assign dependency_convergence_test_label = "UNAVAILABLE">
          </#if>
          <td class='${dependency_convergence_test_label}' title="${row.getExceptionMessage()!""}">${dependency_convergence_test_label}</td>
        </tr>
      </#list>
      </table>
      <p id='updated'>Last generated at ${lastUpdated}</p>
  </body>
</html>