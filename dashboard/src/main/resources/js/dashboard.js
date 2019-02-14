/*
 * Copyright 2019 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Toggles the visibility of source class list below the button.
 * @param button clicked button element
 */
function toggleSourceClassListVisibility(button) {
  const classList = button.parentElement.nextElementSibling;
  const currentVisibility = classList.style.display !== "none";
  const nextVisibility = !currentVisibility;
  classList.style.display = nextVisibility ? "" : "none";
  button.innerText = nextVisibility ? "▼" : "▶";
}
