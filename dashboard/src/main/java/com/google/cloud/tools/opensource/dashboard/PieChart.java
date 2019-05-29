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

import java.awt.geom.Point2D;

public class PieChart {

  /**
   * Calculate SVG arc end for a pie piece. Assumes the piece starts at the top of the circle.
   * 
   * Do not forget:
   * 
   * 1. SVG origin starts at top left.
   * 2. x increases to the right and y increases **down**.
   */
  static Point2D calculateEndPoint(double radius, double centerX, double centerY, double ratio) {
    if (ratio > 1) {
      ratio = 1.0;
    }
    
    double radians = ratio * 2 * Math.PI;
    
    // Since we're starting at the top of the circle this is rotated 90 degrees
    // from the normal coordinates. This is why we use sine for x and cosine for y.
    double x = radius * (1 + Math.sin(radians));
    double y = radius * (1 - Math.cos(radians));
    return new Point2D.Double(x + centerX - radius, y + centerY - radius);
  }

  // so I can avoid teaching FreeMarker how to wrap a java.awt.Point
  public static double calculateEndPointX(
      double radius, double centerX, double centerY, double ratio) {
    return calculateEndPoint(radius, centerX, centerY, ratio).getX();
  }

  public static double calculateEndPointY(
      double radius, double centerX, double centerY, double ratio) {
    return calculateEndPoint(radius, centerX, centerY, ratio).getY();
  }
}
