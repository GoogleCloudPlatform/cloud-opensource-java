/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.opensource.dashboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.common.io.LineProcessor;

class CoordinatesExtractor implements LineProcessor<List<String>> {

  private final List<String> result = new ArrayList<>();
  
  
  private Pattern pattern = Pattern.compile("\\[INFO\\]\s+([-\\w\\d\\.\\:]+)\\:compile");
  
  @Override
  public boolean processLine(String line) throws IOException {
    Matcher matcher = pattern.matcher(line);
    if (matcher.matches()) {
      String coordinates = matcher.group(1);
      result.add(coordinates);
    }
    return true;
  }

  @Override
  public List<String> getResult() {
    return result;
  }

}
