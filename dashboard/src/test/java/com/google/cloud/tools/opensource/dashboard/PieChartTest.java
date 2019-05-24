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
import org.junit.Assert;
import org.junit.Test;

public class PieChartTest {
  
  private static final double TOLERANCE = 0.0001;

  @Test
  public void testRightAngle() {
    Point2D actual = PieChart.calculateEndPoint(100, 100, 100, .25);
    Point2D expected = new Point2D.Double(200, 100);
    Assert.assertEquals(expected.getX(), actual.getX(), TOLERANCE);
    Assert.assertEquals(expected.getY(), actual.getY(), TOLERANCE);
  }
  
  @Test
  public void testZero() {
    Point2D actual = PieChart.calculateEndPoint(100, 100, 100, 0);
    Point2D expected = new Point2D.Double(100, 0);
    Assert.assertEquals(expected.getX(), actual.getX(), TOLERANCE);
    Assert.assertEquals(expected.getY(), actual.getY(), TOLERANCE);
  }
  
  @Test
  public void testOneEighth() {
    int radius = 100;
    double ratio = 1.0/8.0;
    Point2D actual = PieChart.calculateEndPoint(radius, 100, 100, ratio);    
    Point2D expected = new Point2D.Double(100 + 100 / Math.sqrt(2), 100 - 100 / Math.sqrt(2));
    Assert.assertEquals(expected.getX(), actual.getX(), TOLERANCE);
    Assert.assertEquals(expected.getY(), actual.getY(), TOLERANCE);
  }
  
  @Test
  public void testHalf() {
    Point2D actual = PieChart.calculateEndPoint(100, 100, 100, 0.5);
    Point2D expected = new Point2D.Double(100, 200);
    Assert.assertEquals(expected.getX(), actual.getX(), TOLERANCE);
    Assert.assertEquals(expected.getY(), actual.getY(), TOLERANCE);
  }

  @Test
  public void testOffCenter() {
    Point2D actual = PieChart.calculateEndPoint(150, 150, 100, 0.5);
    Point2D expected = new Point2D.Double(150, 250);
    Assert.assertEquals(expected.getX(), actual.getX(), TOLERANCE);
    Assert.assertEquals(expected.getY(), actual.getY(), TOLERANCE);
  }
 
  @Test
  public void testFull() {
    Point2D actual = PieChart.calculateEndPoint(100, 100, 100, 1.0);
    Point2D expected = new Point2D.Double(100, 0);
    Assert.assertEquals(expected.getX(), actual.getX(), TOLERANCE);
    Assert.assertEquals(expected.getY(), actual.getY(), TOLERANCE);  }
  
  @Test
  public void testMoreThanFull() {
    Point2D actual = PieChart.calculateEndPoint(100, 100, 100, 5.5);
    Point2D expected = new Point2D.Double(100, 0);
    Assert.assertEquals(expected.getX(), actual.getX(), TOLERANCE);
    Assert.assertEquals(expected.getY(), actual.getY(), TOLERANCE);
  }
  
}



