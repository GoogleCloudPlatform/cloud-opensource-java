let bomPathElement = '';
let bomCoordinates = '';
const searchParams = new URLSearchParams(window.location.search);

if (searchParams.has('bom')) {
  bomCoordinates = searchParams.get('bom');
  bomPathElement = bomCoordinates.replace(/\:/g, '_') + '/';
}

const prepareTable = async () => {
  console.log("preparing table");

  const searchParams = new URLSearchParams(window.location.search);

  const artifactsParam = searchParams.get('artifacts');

  const artifacts = artifactsParam.split(",");

  const tableElement = $('#dependency-table tbody');

  const columnHeaderElement = $('<tr>');
  tableElement.append(columnHeaderElement);
  const dummyColumnHeader = $('<th rowspan="2" colspan="2">');
  columnHeaderElement.append(dummyColumnHeader);

  const inherentLinkageErrors = new Map();
  const linkageCheckFailures = new Map();

  const promises = artifacts.map((artifact) => {
    const pairFileName = pairToFile(artifact, artifact);
    return $.getJSON(pairFileName);
  });
  const results = await Promise.all(promises);

  let groupId = '';
  let artifactId = '';
  let columnHeaderGA = $('<th>'); // GroupId and ArtifactId
  for (let i = 0; i < artifacts.length; ++i) {
    const artifact1 = artifacts[i];
    if (results[i].references) {
      const symbolProblemKeys = new Set(Object.keys(results[i].references));
      inherentLinkageErrors.set(artifact1, symbolProblemKeys);
    } else if (results[i].error) {
      linkageCheckFailures.set(artifact1, results[i].error);
      inherentLinkageErrors.set(artifact1, new Set());
    }

    const elems = artifact1.split(':');

    // https://www.w3.org/TR/html4/struct/tables.html
    if (groupId === elems[0] && artifactId === elems[1]) {
      const existingColspan = parseInt(columnHeaderGA.attr('colspan'), 10);
      columnHeaderGA.attr('colspan', 1 + existingColspan);
    } else {
      columnHeaderGA = $('<th>');
      columnHeaderGA.html(elems[0] + '<br>' + elems[1]);
      columnHeaderElement.append(columnHeaderGA);
      groupId = elems[0];
      artifactId = elems[1];
      columnHeaderGA.attr('colspan', 1);
    }
  }

  const versionHeaderElement = $('<tr>');
  tableElement.append(versionHeaderElement);
  for (let i = 0; i < artifacts.length; ++i) {
    const artifact1 = artifacts[i];
    const columnHeader = $('<th>');
    const elems = artifact1.split(':');

    columnHeader.text(elems[2]); // version
    versionHeaderElement.append(columnHeader);

    columnHeader.append(createCheckbox(artifact1));
  }

  groupId = '';
  artifactId = '';
  let rowHeaderGA;
  for (let i = 0; i < artifacts.length; ++i) {
    const artifact1 = artifacts[i];

    const tableRowElement = $('<tr>');
    tableElement.append(tableRowElement);

    const elems = artifact1.split(':');
    if (groupId === elems[0] && artifactId === elems[1]) {
      const existingRowSpan = parseInt(rowHeaderGA.attr('rowSpan'), 10);
      rowHeaderGA.attr('rowSpan', 1 + existingRowSpan);
    } else {
      rowHeaderGA = $('<th rowSpan="1">').text(elems[0] + ':' + elems[1]);
      tableRowElement.append(rowHeaderGA);
      groupId = elems[0];
      artifactId = elems[1];
    }
    const rowHeaderVersion = $('<th>').text(elems[2]);
    tableRowElement.append(rowHeaderVersion);

    rowHeaderVersion.append(createCheckbox(artifact1));

    for (let j = 0; j < artifacts.length; ++j) {
      const artifact2 = artifacts[j];

      const tableCellElement = $('<td>');
      tableRowElement.append(tableCellElement);
      tableCellElement.url = "cell.html?artifact1=" + artifact1 + "&artifact2=" + artifact2;
      if (bomPathElement) {
        tableCellElement.url += '&bom=' + bomCoordinates;
      }
      tableCellElement.attr('title', artifact1 + " - " + artifact2);
      tableCellElement.addClass("artifact-row-" + artifact1.replace(/[\:\.]/g, '_'));
      tableCellElement.addClass("artifact-col-" + artifact2.replace(/[\:\.]/g, '_'));

      if (areSameArtifactDifferentVersion(artifact1, artifact2)) {
        tableCellElement.text("N/A");
        continue;
      }
      if (artifact1 === artifact2) {
        tableCellElement.addClass('artifact-same');
      }
      if (artifact1 === artifact2 && linkageCheckFailures.get(artifact1)) {
        tableCellElement.text("error1");
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
          fillTableCell(tableCellElement, linkageCheckResult, inherentErrors, artifact1, artifact2);
        },
        error: (jqXHR, textStatus, errorThrown) => {
          fillTableCellError(tableCellElement, textStatus);
        }
      });
    }
  }
};

const createCheckbox = (artifactCoordinates) => {
  const elems = artifactCoordinates.split(':');
  const classFriendlyCoordinates = artifactCoordinates.replace(/[\:\.]/g, '_');
  const groupName = elems[0] + '_' + elems[1];
  const groupQuery = "input:checkbox[name='" + groupName + "']";
  const checkbox = $('<input type="checkbox" class="artifact-checkbox" name="'+ groupName +'"/>');
  checkbox.data('coordinates', artifactCoordinates);
  checkbox.on('click', (event) => {
    const element = $(event.currentTarget);
    if (element.is(":checked")) {
      $('.artifact-col-' + classFriendlyCoordinates).addClass('artifact-col-selected');
      $('.artifact-row-' + classFriendlyCoordinates).addClass('artifact-row-selected');

      // Mark other checkbox
      const sameArtifactGroup = $(groupQuery);
      sameArtifactGroup.each((index, inputElement) => {
        const element = $(inputElement);
        const otherCoordinates = element.data('coordinates');
        const checked = otherCoordinates === artifactCoordinates;
        element.prop("checked", checked);
        if (!checked) {
          const classFriendlyOtherCoordinates = otherCoordinates.replace(/[\:\.]/g, '_');
          $('.artifact-col-' + classFriendlyOtherCoordinates).removeClass('artifact-col-selected');
          $('.artifact-row-' + classFriendlyOtherCoordinates).removeClass('artifact-row-selected');
          $('.artifact-col-' + classFriendlyOtherCoordinates).addClass('artifact-col-unselected');
          $('.artifact-row-' + classFriendlyOtherCoordinates).addClass('artifact-row-unselected');
        }
      });
    } else {

      const sameArtifactGroup = $(groupQuery);
      sameArtifactGroup.each((index, inputElement) => {
        const element = $(inputElement);
        const otherCoordinates = element.data('coordinates');
        if (artifactCoordinates === otherCoordinates) {
          $(inputElement).prop('checked', false);
        }
        const classFriendlyOtherCoordinates = otherCoordinates.replace(/[\:\.]/g, '_');
        $('.artifact-col-' + classFriendlyOtherCoordinates).removeClass('artifact-col-unselected');
        $('.artifact-row-' + classFriendlyOtherCoordinates).removeClass('artifact-row-unselected');
      });
      $('.artifact-col-' + classFriendlyCoordinates).removeClass('artifact-col-selected');
      $('.artifact-row-' + classFriendlyCoordinates).removeClass('artifact-row-selected');
    }
  });
  return checkbox;
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

  const header = $('#artifact-pair');
  header.text(artifact1 + " x " + artifact2);

  const linkageCheckResult = await $.getJSON(pairFileName);

  if (linkageCheckResult.error) {
    $('#linkage-check-failure').text(linkageCheckResult.error);
    return;
  }
  
  const symbolProblems = linkageCheckResult.symbolProblems;
  const references = linkageCheckResult.references;

  const listElement = $('#symbol-problems');

  Object.keys(symbolProblems).forEach(problem => {
    if (problems1.has(problem) || problems2.has(problem)) {
      return;
    }
    const listItem = $('<li>').text(problem);
    listElement.append(
        listItem
    );
    const referencingClasses = references[problem];
    const referencingClassElement = $('<ul>');
    listItem.append(referencingClassElement);
    referencingClasses.forEach(item => {
      const line = item.className + " (" +  item.coordinates +")";
      referencingClassElement.append($('<li>').text(line));
    });
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

  const classPathList = $('#class-path');
  linkageCheckResult.classPathArtifacts.forEach(item => {
    classPathList.append($('<li>').text(item));
  })
};

const fetchSymbolProblems = async (artifact) => {
  const pairFileName = pairToFile(artifact, artifact);
  const linkageCheckResult = await $.getJSON(pairFileName);
  return new Set(Object.keys(linkageCheckResult.symbolProblems));
};

const pairToFile = (artifact1, artifact2) => {

  const pairFileName = 'linkage-check-cache/'
      + bomPathElement
      + artifact1.replace(/\:/g, '_')
      + '___'
      + artifact2.replace(/\:/g, '_')
      + '.json';
  return pairFileName;
};

const fillTableCell = (tableCellElement, linkageCheckResult, inherentLinkageErrors, artifact1, artifact2) => {
  let nonInherentCount = 0;
  const anchor = $('<a>');
  if (linkageCheckResult.error) {
    anchor.text("error1");
    tableCellElement.addClass('has-error');
  } else {
    if (artifact1 === artifact2) {
      anchor.text("(" + inherentLinkageErrors.size + ")");
    } else {
      Object.keys(linkageCheckResult.symbolProblems).forEach(symbolProblem => {
        if (inherentLinkageErrors.has(symbolProblem)) {
          return;
        }
        nonInherentCount++;
      });
      anchor.text(nonInherentCount);
      if (nonInherentCount > 0 && artifact1 !== artifact2) {
        tableCellElement.addClass('has-error');
      }
    }
  }

  anchor.attr('href', tableCellElement.url);
  tableCellElement.append(anchor);
};

const fillTableCellError = (tableCellElement, textStatus) => {
  tableCellElement.text(textStatus);
};



