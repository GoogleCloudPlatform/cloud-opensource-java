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

package com.google.cloud.tools.opensource.dashboard;

import java.awt.Point;

public class PieChart {

  /**
   * Calculate SVG arc end for a pie piece. Assumes the piece starts at the top of the circle.
   * 
   * Do not forget:
   * 
   * 1. SVG origin starts at top left.
   * 2. x increases to the right and y increases **down**.
   * 3. SVG coordinates are integers.
   */
  static Point calculateEndPoint(int radius, int centerX, int centerY, double ratio) {
    if (ratio > 1) {
      ratio = 1.0;
    }
    
    double radians = ratio * 2 * Math.PI;
    
    // Since we're starting at the top of the circle this is rotated 90 degrees
    // from the normal coordinates. This is why we use sine for x and cosine for y.
    int dx = (int) (radius * Math.sin(radians));
    int dy = (int) -(radius * Math.cos(radians));
    return new Point(centerX + dx, centerY + dy);
  }
  
  // so I can avoid teaching FreeMarker how to wrap a java.awt.Point
  public static int calculateEndPointX(int radius, int centerX, int centerY, double ratio) {
    return calculateEndPoint(radius, centerX, centerY, ratio).x;
  }
  
  public static int calculateEndPointY(int radius, int centerX, int centerY, double ratio) {
    return calculateEndPoint(radius, centerX, centerY, ratio).y;
  }
}
