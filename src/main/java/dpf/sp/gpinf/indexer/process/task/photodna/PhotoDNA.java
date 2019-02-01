package dpf.sp.gpinf.indexer.process.task.photodna;

/////////////////////////////////////////////////////////////////////////////////
//
//IMPORTANT NOTICE
//================
//
//Copyright (C) 2016, 2017, Microsoft Corporation
//All Rights Reserved.
//
//THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS STRICTLY UNDER THE TERMS
//OF A SEPARATE LICENSE AGREEMENT.  NO SOURCE CODE DISTRIBUTION OR SOURCE 
//CODE DISCLOSURE RIGHTS ARE PROVIDED WITH RESPECT TO THIS SOFTWARE OR ANY 
//DERIVATIVE WORKS THEREOF.  USE AND NON-SOURCE CODE REDISTRIBUTION OF THE 
//SOFTWARE IS PERMITTED ONLY UNDER THE TERMS OF SUCH LICENSE AGREEMENT.  
//THIS SOFTWARE AND ANY WORKS/MODIFICATIONS/TRANSLATIONS DERIVED FROM THIS 
//SOFTWARE ARE COVERED BY SUCH LICENSE AGREEMENT AND MUST ALSO CONTAIN THIS 
//NOTICE.  THIS SOFTWARE IS CONFIDENTIAL AND SENSITIVE AND MUST BE SECURED 
//WITH LIMITED ACCESS PURSUANT TO THE TERMS OF SUCH LICENSE AGREEMENT.
//
//THE SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
//INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY 
//AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
//COPYRIGHT HOLDER BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
//EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
//PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
//OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
//WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR 
//OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
//ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
//Project:       PhotoDNA Robust Hashing
//File:          PhotoDNA.java
//Description:   Java wrapper class for PhotoDNA libraries
//History:   
//2016.09.13   adrian chandley  
//Created Java Class to enable Java apps to call PhotoDNA libraries
//
/////////////////////////////////////////////////////////////////////////////////

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Native;

public class PhotoDNA {

    // Error Codes
    public static final int S_OK = 0x0000;
    public static final int COMPUTE_HASH_FAILED = 0x0200;
    public static final int COMPUTE_HASH_BAD_ARGUMENT = 0x0201;
    public static final int COMPUTE_HASH_NO_HASH_CALCULATED = 0x0202;
    public static final int COMPUTE_HASH_NO_HASH_CALCULATED_IMAGE_TOO_SMALL = 0x0213;
    public static final int COMPUTE_HASH_NO_HASH_CALCULATED_IMAGE_IS_FLAT = 0x0214;

    public static final int HASH_SIZE = 144;
    public static final int SHORT_HASH_SIZE = 64;
    public static final int CHECK_SIZE = 36;
    public static final int NCMEC_TARGET = 41943;
    
    private static File libPath;
    
    private static boolean setuped = false;

    // Private data
    long hashBuffer = 0;
    
    PhotoDNAJNA photodna;
    
    public static void setLibPath(File path) {
        libPath = path;
    }
    
    private static synchronized void setup() {
        if(setuped) return;
        
        String dllname = "PhotoDNA";

        String osArch = System.getProperty("os.arch");
        String osData = System.getProperty("sun.arch.data.model");

        if (osArch.equals("aarch32")) {
            dllname += "arm32";
        } else if (osArch.equals("aarch64")) {
            dllname += "arm64";
        } else if (osArch.equals("arm")) {
            if (osData != null && osData.equals("32")) {
                dllname += "arm32";
            } else {
                dllname += "arm64";
            }
        } else if (osArch.equals("amd64") || osArch.equals("x86_64")) {
            dllname += "x64";
        } else {
            dllname += "x86";
        }
        if (System.getProperty("os.name").toLowerCase().startsWith("mac os x")) {
            dllname += "-osx";
        }
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            dllname += ".1.72.dll";
        } else {
            dllname += ".so.1.72";
        }

        try {
            System.out.println("Loading " + dllname);
            String path2 = new File(libPath, dllname).getCanonicalPath();
            System.load(path2);
            
            setuped = true;
            
        } catch (Throwable ex2) {
            throw new RuntimeException(ex2);
        }
    }

    public PhotoDNA() {
        this(0);
    }

    public PhotoDNA(int initialSize) {
        if(!setuped)
            setup();
        
        photodna = (PhotoDNAJNA) Native.load(PhotoDNAJNA.class);
        hashBuffer = photodna.RobustHashInitBuffer(initialSize);
    }
    
    public int distance(byte[] hash1, byte[] hash2){
      int distance = 0;
      for (int i = 0; i < HASH_SIZE; i++) {
        int diff = (0xFF & hash1[i]) - (0xFF & hash2[i]);
        distance += diff * diff;
      }
      return distance;
    }
    
    public void reset() {
        photodna.RobustHashResetBuffer(hashBuffer);
    }

    public int ComputeHash(byte[] imageData, int width, int height, int stride, byte[] hashValue) {
        return photodna.ComputeRobustHash(imageData, width, height, stride, hashValue, hashBuffer);
    }

    public int ComputeHashAltColor(byte[] imageData, int width, int height, int stride, int color_type,
            byte[] hashValue) {
        return photodna.ComputeRobustHashAltColor(imageData, width, height, stride, color_type, hashValue, hashBuffer);
    }

    public int ComputeHashBorder(byte[] imageData, int width, int height, int stride, int color_type, int[] border,
            byte[] hashValue, byte[] hashValueTrimmed) {
        return photodna.ComputeRobustHashBorder(imageData, width, height, stride, color_type, border, hashValue,
                hashValueTrimmed, hashBuffer);
    }

    public int ComputeHashAdvanced(byte[] imageData, int width, int height, int stride, int color_type, int sub_x,
            int sub_y, int sub_w, int sub_h, int[] border, byte[] hashValue, byte[] hashValueTrimmed) {
        return photodna.ComputeRobustHashAdvanced(imageData, width, height, stride, color_type, sub_x, sub_y, sub_w, sub_h,
                border, hashValue, hashValueTrimmed, hashBuffer);
    }

    public int ComputeShort(byte[] imageData, int width, int height, int stride, int color_type,
            byte[] shortHashValue) {
        return photodna.ComputeShortHash(imageData, width, height, stride, color_type, shortHashValue, hashBuffer);
    }

    public int ComputeCheck(byte[] imageData, int width, int height, int stride, int color_type, byte[] checkValue) {
        return photodna.ComputeCheckArray(imageData, width, height, stride, color_type, checkValue, hashBuffer);
    }

    public int HashVersion() {
        return photodna.RobustHashVersion();
    }
    
    @Override
    protected void finalize() throws Throwable {
        try {
            photodna.RobustHashReleaseBuffer(hashBuffer);
        } catch (Throwable t) {
            throw t;
        } finally {
            super.finalize();
        }
    }
}
