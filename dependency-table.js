const prepareTable = async () => {
  console.log("preparing table");

  const searchParams = new URLSearchParams(window.location.search);

  const artifactsParam = searchParams.get('artifacts');

  const artifacts = artifactsParam.split(",");

  const tableElement = $('#dependency-table tbody');

  const columnHeaderElement = $('<tr>');
  tableElement.append(columnHeaderElement);
  const dummyColumnHeader = $('<th>');
  columnHeaderElement.append(dummyColumnHeader);

  const inherentLinkageErrors = new Map();


  const promises = artifacts.map((artifact) => {
    const pairFileName = pairToFile(artifact, artifact);
    return $.getJSON(pairFileName);
  });
  const results = await Promise.all(promises);

  for (let i = 0; i < artifacts.length; ++i) {
    const artifact1 = artifacts[i];
    const symbolProblemKeys = new Set(Object.keys(results[i].references));
    inherentLinkageErrors.set(artifact1, symbolProblemKeys);

    const columnHeader = $('<th>');
    columnHeader.text(artifact1);
    columnHeaderElement.append(columnHeader);
  }

  for (let i = 0; i < artifacts.length; ++i) {
    const artifact1 = artifacts[i];

    const tableRowElement = $('<tr>');
    tableElement.append(tableRowElement);

    const rowHeader = $('<th>');
    rowHeader.text(artifact1);
    tableRowElement.append(rowHeader);


    for (let j = 0; j < artifacts.length; ++j) {
      const artifact2 = artifacts[j];

      const tableCellElement = $('<td>');
      tableRowElement.append(tableCellElement);
      tableCellElement.url = "cell.html?artifact1=" + artifact1 + "&artifact2=" + artifact2;

      if (areSameArtifactDifferentVersion(artifact1, artifact2)) {
        tableCellElement.text("N/A");
        continue;
      }

      const pairFileName = pairToFile(artifact1, artifact2);

      const inherentErrors = new Set();
      inherentLinkageErrors.get(artifact1).forEach(item => inherentErrors.add(item));
      inherentLinkageErrors.get(artifact2).forEach(item => inherentErrors.add(item));

      $.ajax({
        dataType: "json",
        url: pairFileName,
        success: (linkageCheckResult) => {
          fillTableCell(tableCellElement, linkageCheckResult, inherentErrors);
        },
        error: (jqXHR, textStatus, errorThrown) => {
          fillTableCellError(tableCellElement, textStatus);
        }
      });

    }
  }
};

const areSameArtifactDifferentVersion = (artifact1, artifact2) => {
  const elements1 = artifact1.split(":");
  const elements2 = artifact2.split(":");

  if (elements1[2] === elements2[2]) {
    return false;
  }
  if (elements1[0] === elements2[0] && elements1[1] === elements2[1]) {
    return true;
  }
  return false;
};

const prepareCell = async () => {
  const searchParams = new URLSearchParams(window.location.search);

  const artifact1 = searchParams.get('artifact1');
  const artifact2 = searchParams.get('artifact2');

  const pairFileName = pairToFile(artifact1, artifact2);

  const problems1 = await fetchSymbolProblems(artifact1);
  const problems2 = await fetchSymbolProblems(artifact2);

  const linkageCheckResult = await $.getJSON(pairFileName);

  const symbolProblems = linkageCheckResult.symbolProblems;

  const header = $('#artifact-pair');
  header.text(artifact1 + " x " + artifact2);

  const listElement = $('#symbol-problems');

  Object.keys(symbolProblems).forEach(problem => {
    if (problems1.has(problem) || problems2.has(problem)) {
      return;
    }
    listElement.append(
        $('<li>').text(problem)
    );
  });

  $('#artifact1 .artifact-name').text(artifact1);

  const listElement1 = $('#artifact1 .problem-list');
  problems1.forEach(problem => {
    listElement1.append(
        $('<li>').text(problem)
    );
  });


  $('#artifact2 .artifact-name').text(artifact2);
  const listElement2 = $('#artifact2 .problem-list');
  problems2.forEach(problem => {
    listElement2.append(
        $('<li>').text(problem)
    );
  });
};

const fetchSymbolProblems = async (artifact) => {
  const pairFileName = pairToFile(artifact, artifact);
  const linkageCheckResult = await $.getJSON(pairFileName);
  return new Set(Object.keys(linkageCheckResult.symbolProblems));
};

const pairToFile = (artifact1, artifact2) => {
  const pairFileName = 'linkage-check-cache/'
      + artifact1.replace(/\:/g, '_')
      + '___'
      + artifact2.replace(/\:/g, '_')
      + '.json';
  return pairFileName;
};

const fillTableCell = (tableCellElement, linkageCheckResult, inherentLinkageErrors) => {
  console.log(linkageCheckResult);

  let nonInherentCount = 0;
  Object.keys(linkageCheckResult.symbolProblems).forEach(symbolProblem => {
    if (inherentLinkageErrors.has(symbolProblem)) {
      return;
    }
    nonInherentCount++;
  });

  const anchor = $('<a>').text(nonInherentCount);
  anchor.attr('href', tableCellElement.url);

  tableCellElement.append(anchor);
};

const fillTableCellError = (tableCellElement, textStatus) => {
  tableCellElement.text(textStatus);
};



