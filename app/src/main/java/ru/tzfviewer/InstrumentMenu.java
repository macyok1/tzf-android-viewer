package ru.tzfviewer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.List;

final class InstrumentMenu {
    static final class Action {
        final String icon,title,subtitle,trailing;final boolean selected,danger,dividerBefore;final Runnable run;
        Action(String icon,String title,String subtitle,String trailing,boolean selected,boolean danger,boolean dividerBefore,Runnable run){this.icon=icon;this.title=title;this.subtitle=subtitle;this.trailing=trailing;this.selected=selected;this.danger=danger;this.dividerBefore=dividerBefore;this.run=run;}
        static Action item(String icon,String title,String subtitle,Runnable run){return new Action(icon,title,subtitle,"",false,false,false,run);}
        Action state(String value,boolean active){return new Action(icon,title,subtitle,value,active,danger,dividerBefore,run);}
        Action danger(){return new Action(icon,title,subtitle,trailing,selected,true,dividerBefore,run);}
        Action divided(){return new Action(icon,title,subtitle,trailing,selected,danger,true,run);}
    }

    private InstrumentMenu(){}
    static void show(View anchor,String eyebrow,List<Action> actions){
        Context context=anchor.getContext();LinearLayout card=card(context,eyebrow);PopupWindow popup=new PopupWindow(card,dp(context,330),ViewGroup.LayoutParams.WRAP_CONTENT,true);popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));popup.setOutsideTouchable(true);popup.setElevation(dp(context,18));
        for(Action action:actions){if(action.dividerBefore)card.addView(divider(context));card.addView(row(context,action,()->{popup.dismiss();if(action.run!=null)action.run.run();}));}
        card.measure(View.MeasureSpec.makeMeasureSpec(dp(context,330),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));int[] point=new int[2];anchor.getLocationOnScreen(point);Rect frame=new Rect();anchor.getWindowVisibleDisplayFrame(frame);int width=card.getMeasuredWidth(),height=card.getMeasuredHeight(),gap=dp(context,8);int x=point[0]+anchor.getWidth()+gap;if(x+width>frame.right-gap)x=point[0]-width-gap;x=Math.max(frame.left+gap,x);int y=Math.max(frame.top+gap,Math.min(point[1]-height/2+anchor.getHeight()/2,frame.bottom-height-gap));popup.showAtLocation(anchor.getRootView(),Gravity.NO_GRAVITY,x,y);
    }
    static LinearLayout card(Context context,String eyebrow){LinearLayout card=new LinearLayout(context);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(context,14),dp(context,12),dp(context,14),dp(context,14));card.setBackgroundResource(R.drawable.bg_instrument_card);TextView label=text(context,eyebrow.toUpperCase(),11,R.color.text_secondary);label.setTypeface(Typeface.MONOSPACE);label.setLetterSpacing(.14f);label.setPadding(dp(context,8),dp(context,4),dp(context,8),dp(context,9));card.addView(label);return card;}
    static View row(Context context,Action action,Runnable click){LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(context,8),dp(context,7),dp(context,8),dp(context,7));row.setMinimumHeight(dp(context,62));row.setBackgroundResource(action.selected?R.drawable.bg_instrument_row_active:R.drawable.bg_instrument_row);TextView icon=text(context,action.icon,12,action.danger?R.color.danger:action.selected?R.color.cyan:R.color.text_primary);icon.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);icon.setGravity(Gravity.CENTER);icon.setBackgroundResource(R.drawable.bg_instrument_icon);row.addView(icon,new LinearLayout.LayoutParams(dp(context,38),dp(context,38)));LinearLayout words=new LinearLayout(context);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(context,10),0,dp(context,6),0);TextView title=text(context,action.title,15,action.danger?R.color.danger:R.color.text_primary);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);words.addView(title);if(action.subtitle!=null&&!action.subtitle.isEmpty()){TextView subtitle=text(context,action.subtitle,11,R.color.text_secondary);subtitle.setPadding(0,dp(context,2),0,0);words.addView(subtitle);}row.addView(words,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));if(action.trailing!=null&&!action.trailing.isEmpty()){TextView trailing=text(context,action.trailing,10,action.danger?R.color.danger:R.color.cyan);trailing.setTypeface(Typeface.MONOSPACE);trailing.setGravity(Gravity.CENTER);trailing.setPadding(dp(context,7),dp(context,4),dp(context,7),dp(context,4));trailing.setBackgroundResource(R.drawable.bg_instrument_pill);row.addView(trailing);}row.setContentDescription(action.title+(action.trailing==null||action.trailing.isEmpty()?"":". "+action.trailing));row.setOnClickListener(v->click.run());return row;}
    static View divider(Context context){View line=new View(context);line.setBackgroundColor(Color.rgb(41,68,83));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(context,1));params.setMargins(dp(context,8),dp(context,7),dp(context,8),dp(context,7));line.setLayoutParams(params);return line;}
    static TextView text(Context context,String value,float size,int color){TextView view=new TextView(context);view.setText(value);view.setTextSize(size);view.setTextColor(context.getColor(color));return view;}
    static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
}
