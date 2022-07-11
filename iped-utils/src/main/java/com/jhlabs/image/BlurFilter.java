/*
Copyright 2006 Jerry Huxtable

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.jhlabs.image;

/**
 * A simple blur filter. You should probably use BoxBlurFilter instead.
 */
public class BlurFilter extends ConvolveFilter {
 	
 	/**
     * A 3x3 convolution kernel for a simple blur.
     */
    protected static float[] blurMatrix = {
		1/14f, 2/14f, 1/14f,
		2/14f, 2/14f, 2/14f,
		1/14f, 2/14f, 1/14f
	};

	public BlurFilter() {
		super( blurMatrix );
	}

	public String toString() {
		return "Blur/Simple Blur";
	}
}
