package iped.app.home.style;

/*
 * @created 15/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import java.awt.*;

public class StyleManager {


    public static Font getPageTitleFont(){
        return new Font("Arial Bold", Font.PLAIN, 28);
    }

    public static Font getHomeButtonFont(){
        return new Font("Arial Bold", Font.PLAIN, 20);
    }

    public static Insets getDefaultPanelInsets(){
        return new Insets(20, 20, 20, 20);
    }


}
