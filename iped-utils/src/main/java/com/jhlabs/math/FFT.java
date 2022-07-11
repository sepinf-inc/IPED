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

package com.jhlabs.math;

public class FFT {

    // Weighting factors
    protected float[] w1;
    protected float[] w2;
    protected float[] w3;

    public FFT( int logN ) {
        // Prepare the weighting factors
        w1 = new float[logN];
        w2 = new float[logN];
        w3 = new float[logN];
        int N = 1;
        for ( int k = 0; k < logN; k++ ) {
            N <<= 1;
            double angle = -2.0 * Math.PI / N;
            w1[k] = (float)Math.sin(0.5 * angle);
            w2[k] = -2.0f * w1[k] * w1[k];
            w3[k] = (float)Math.sin(angle);
        }
    }

    private void scramble( int n, float[] real, float[] imag ) {
        int j = 0;

        for ( int i = 0; i < n; i++ ) {
            if ( i > j ) {
                float t;
                t = real[j];
                real[j] = real[i];
                real[i] = t;
                t = imag[j];
                imag[j] = imag[i];
                imag[i] = t;
            }
            int m = n >> 1;
            while (j >= m && m >= 2) {
                j -= m;
                m >>= 1;
            }
            j += m;
        }
    }

    private void butterflies( int n, int logN, int direction, float[] real, float[] imag ) {
        int N = 1;

        for ( int k = 0; k < logN; k++ ) {
            float w_re, w_im, wp_re, wp_im, temp_re, temp_im, wt;
            int half_N = N;
            N <<= 1;
            wt = direction * w1[k];
            wp_re = w2[k];
            wp_im = direction * w3[k];
            w_re = 1.0f;
            w_im = 0.0f;
            for ( int offset = 0; offset < half_N; offset++ ) {
                for( int i = offset; i < n; i += N ) {
                    int j = i + half_N;
                    float re = real[j];
                    float im = imag[j];
                    temp_re = (w_re * re) - (w_im * im);
                    temp_im = (w_im * re) + (w_re * im);
                    real[j] = real[i] - temp_re;
                    real[i] += temp_re;
                    imag[j] = imag[i] - temp_im;
                    imag[i] += temp_im;
                }
                wt = w_re;
                w_re = wt * wp_re - w_im * wp_im + w_re;
                w_im = w_im * wp_re + wt * wp_im + w_im;
            }
        }
        if ( direction == -1 ) {
            float nr = 1.0f / n;
            for ( int i = 0; i < n; i++ ) {
                real[i] *= nr;
                imag[i] *= nr;
            }
        }
    }

    public void transform1D( float[] real, float[] imag, int logN, int n, boolean forward ) {
        scramble( n, real, imag );
        butterflies( n, logN, forward ? 1 : -1, real, imag );
    }

    public void transform2D( float[] real, float[] imag, int cols, int rows, boolean forward ) {
        int log2cols = log2(cols);
        int log2rows = log2(rows);
        int n = Math.max(rows, cols);
        float[] rtemp = new float[n];
        float[] itemp = new float[n];

        // FFT the rows
        for ( int y = 0; y < rows; y++ ) {
            int offset = y*cols;
            System.arraycopy( real, offset, rtemp, 0, cols );
            System.arraycopy( imag, offset, itemp, 0, cols );
            transform1D(rtemp, itemp, log2cols, cols, forward);
            System.arraycopy( rtemp, 0, real, offset, cols );
            System.arraycopy( itemp, 0, imag, offset, cols );
        }

        // FFT the columns
        for ( int x = 0; x < cols; x++ ) {
            int index = x;
            for ( int y = 0; y < rows; y++ ) {
                rtemp[y] = real[index];
                itemp[y] = imag[index];
                index += cols;
            }
            transform1D(rtemp, itemp, log2rows, rows, forward);
            index = x;
            for ( int y = 0; y < rows; y++ ) {
                real[index] = rtemp[y];
                imag[index] = itemp[y];
                index += cols;
            }
        }
    }

    private int log2( int n ) {
        int m = 1;
        int log2n = 0;

        while (m < n) {
            m *= 2;
            log2n++;
        }
        return m == n ? log2n : -1;
    }

}
