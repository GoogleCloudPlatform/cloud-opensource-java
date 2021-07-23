// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// This file contains JavaScript-applied rules that can be applied
// to documentation sites using this Jekyll theme generally.
$.when($.ready).then(() => {
  // Control the maximum height of the nav sidebar.
  $(window)
    .on('resize', () => {
      $('nav.docs-component-nav').css({
        maxHeight: `${$(window).height() - 110}px`,
      });
    })
    .resize();

  $('h1').addClass('glue-headline').addClass('glue-has-top-margin');
  $('h2').addClass('glue-headline').addClass('glue-has-top-margin');
  $('h3').addClass('glue-headline').addClass('glue-has-top-margin');
  $('h4').addClass('glue-headline').addClass('glue-has-top-margin');

  $('h1').addClass('glue-headline--headline-2').addClass('glue-has-bottom-margin');
  $('h2').addClass('glue-headline--headline-3').addClass('glue-has-bottom-margin');
  $('h3').addClass('glue-headline--headline-4').addClass('glue-has-bottom-margin');
  $('h4').addClass('glue-headline--headline-5');
});
