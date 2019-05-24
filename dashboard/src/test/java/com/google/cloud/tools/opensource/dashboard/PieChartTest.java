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
import org.junit.Assert;
import org.junit.Test;

public class PieChartTest {
  
  @Test
  public void testRightAngle() {
    Point actual = PieChart.calculateEndPoint(100, 100, 100, .25);
    Point expected = new Point(200, 100);
    Assert.assertEquals(expected, actual);
  }
  
  @Test
  public void testZero() {
    Point actual = PieChart.calculateEndPoint(100, 100, 100, 0);
    Point expected = new Point(100, 0);
    Assert.assertEquals(expected, actual);
  }
  
  @Test
  public void testOneEighth() {
    Point actual = PieChart.calculateEndPoint(100, 100, 100, 1.0/8.0);
    Point expected = new Point(170, 30);
    Assert.assertEquals(expected, actual);
  }
  
  @Test
  public void testHalf() {
    Point actual = PieChart.calculateEndPoint(100, 100, 100, 0.5);
    Point expected = new Point(100, 200);
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testOffCenter() {
    Point actual = PieChart.calculateEndPoint(150, 150, 100, 0.5);
    Point expected = new Point(150, 250);
    Assert.assertEquals(expected, actual);
  }
 
  @Test
  public void testFull() {
    Point actual = PieChart.calculateEndPoint(100, 100, 100, 1.0);
    Point expected = new Point(100, 0);
    Assert.assertEquals(expected, actual);
  }
  
  @Test
  public void testMoreThanFull() {
    Point actual = PieChart.calculateEndPoint(100, 100, 100, 5.5);
    Point expected = new Point(100, 0);
    Assert.assertEquals(expected, actual);
  }
  
}



