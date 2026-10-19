package br.com.poolaim.overlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Ball-centre cushion ray geometry. Each segment ends at a cushion plane or a
 * pocket opening. No spin/rail elasticity modelling; purely angle-of-reflection.
 */
public final class RailTrace {
    public static final class Segment {
        public final float x1,y1,x2,y2;
        public final boolean pocket, cushion;
        Segment(float x1,float y1,float x2,float y2,boolean pocket,boolean cushion){
            this.x1=x1;this.y1=y1;this.x2=x2;this.y2=y2;this.pocket=pocket;this.cushion=cushion;
        }
    }
    private RailTrace() { }
    public static List<Segment> trace(float x,float y,float dx,float dy,
                                      float tableLeft,float tableTop,float tableRight,float tableBottom,
                                      float radius,int maxSegments){
        ArrayList<Segment> output=new ArrayList<>();
        if(maxSegments<=0 || !Float.isFinite(x) || !Float.isFinite(dx) || !Float.isFinite(dy))return output;
        float m=(float)Math.hypot(dx,dy); if(m<.0001f)return output;
        dx/=m;dy/=m;
        float left=tableLeft+radius, right=tableRight-radius;
        float top=tableTop+radius,bottom=tableBottom-radius;
        float width=right-left,height=bottom-top;
        if(width<=5||height<=5 || x<left-2 || x>right+2 || y<top-2||y>bottom+2)return output;
        x=Math.max(left,Math.min(right,x));y=Math.max(top,Math.min(bottom,y));
        for(int i=0;i<maxSegments;i++){
            float tx=Float.POSITIVE_INFINITY,ty=Float.POSITIVE_INFINITY;
            if(dx>.0001f)tx=(right-x)/dx;else if(dx<-.0001f)tx=(left-x)/dx;
            if(dy>.0001f)ty=(bottom-y)/dy;else if(dy<-.0001f)ty=(top-y)/dy;
            float t=Math.min(tx,ty);
            if(!Float.isFinite(t)||t<-.01f)return output;
            float x2=Math.max(left,Math.min(right,x+dx*Math.max(0,t)));
            float y2=Math.max(top,Math.min(bottom,y+dy*Math.max(0,t)));
            boolean hitVertical=tx<=ty+.01f, hitHorizontal=ty<=tx+.01f;
            // A rough pocket opening width scales with the actual ball radius
            // instead of the user's screen orientation or cloth theme.
            float cornerGap=Math.max(radius*2.6f,Math.min(width,height)*.08f);
            float sideGap=Math.max(radius*2.4f,width*.065f);
            boolean pocket=false;
            if(hitHorizontal) pocket=(x2-left)<cornerGap || (right-x2)<cornerGap
                    || Math.abs(x2-(left+right)*.5f)<sideGap;
            if(hitVertical) pocket |= (y2-top)<cornerGap || (bottom-y2)<cornerGap;
            output.add(new Segment(x,y,x2,y2,pocket,!pocket));
            if(pocket || (Math.abs(x2-x)<.001f && Math.abs(y2-y)<.001f))return output;
            if(hitVertical)dx=-dx;
            if(hitHorizontal)dy=-dy;
            x=x2+dx*.045f;
            y=y2+dy*.045f;
        }
        return output;
    }
}
