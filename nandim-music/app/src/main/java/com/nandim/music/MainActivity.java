package com.nandim.music;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
  private static final int PERMISSION = 101;
  private static final int BG = Color.rgb(12, 20, 32);
  private static final int CARD = Color.rgb(31, 43, 58);
  private static final int WHITE = Color.WHITE;
  private static final int GREEN = Color.rgb(92, 229, 179);
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ArrayList<Track> library = new ArrayList<>();
  private final ArrayList<Track> shown = new ArrayList<>();
  private final ArrayList<String> shownNames = new ArrayList<>();
  private ArrayAdapter<String> adapter;
  private MediaPlayer player;
  private TextView status, playing, clock;
  private SeekBar progress;
  private Button pauseButton;
  private EditText search;
  private int current = -1;
  private int scanId = 0;
  private boolean loaded = false;
  private boolean drag = false;
  private boolean preparing = false;
  private boolean wantPlay = false;
  private final Runnable ticker = new Runnable() {
    public void run() {
      if (player != null && !preparing) {
        try {
          if (!drag) progress.setProgress(player.getCurrentPosition());
          clock.setText(time(player.getCurrentPosition())+" / "+time(player.getDuration()));
        } catch (Exception ignored) {}
      }
      handler.postDelayed(this, 500);
    }
  };
  private static class Track {
    long id; String name, artist; Uri uri; long duration;
    Track(long id, String n, String a, long ms) {
      this.id=id; name=n==null||n.isEmpty()?"Sem título":n;
      artist=a==null||a.isEmpty()?"Artista desconhecido":a;
      duration=ms;
      uri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,id);
    }
  }
  private int dp(int n) { return (int)(getResources().getDisplayMetrics().density*n+.5f); }
  private GradientDrawable bg(int color) {
    GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(14)); return d;
  }
  private TextView label(String text,int size,int color) {
    TextView v=new TextView(this); v.setText(text); v.setTextColor(color); v.setTextSize(size);
    v.setGravity(Gravity.CENTER_VERTICAL); return v;
  }
  private Button button(String title) {
    Button b=new Button(this); b.setText(title); b.setTextColor(WHITE);
    b.setTextSize(19); b.setAllCaps(false); b.setBackground(bg(CARD)); return b;
  }
  private LinearLayout.LayoutParams space(int top) {
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(top); return p;
  }
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
    root.setPadding(dp(16),dp(12),dp(16),dp(12)); root.setFitsSystemWindows(true);
    setContentView(root);
    TextView header=label("♫  Nandim Music",25,GREEN); header.setTypeface(null,Typeface.BOLD);
    root.addView(header);
    status=label("Procurando músicas...",13,WHITE); root.addView(status,space(4));
    search=new EditText(this);
    search.setSingleLine(); search.setHint("Buscar música ou artista");
    search.setTextColor(WHITE); search.setHintTextColor(0xff9daebe);
    search.setTextSize(15); search.setPadding(dp(13),0,dp(13),0); search.setBackground(bg(CARD));
    root.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));
    search.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s,int st,int c,int a){}
      public void onTextChanged(CharSequence s,int st,int before,int count){ filter(s.toString()); }
      public void afterTextChanged(Editable e){}
    });
    ListView list=new ListView(this); list.setDividerHeight(dp(1)); list.setDivider(null);
    adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,shownNames) {
      @Override public View getView(int pos,View v,android.view.ViewGroup parent) {
        TextView result=(TextView)super.getView(pos,v,parent);
        result.setTextColor(WHITE); result.setTextSize(15);
        result.setPadding(dp(12),dp(7),dp(12),dp(7));
        return result;
      }
    };
    list.setAdapter(adapter);
    list.setOnItemClickListener((parent,v,pos,id)->playId(shown.get(pos).id));
    root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout bottom=new LinearLayout(this); bottom.setOrientation(LinearLayout.VERTICAL);
    bottom.setBackground(bg(CARD)); bottom.setPadding(dp(12),dp(10),dp(12),dp(12));
    root.addView(bottom,space(8));
    playing=label("Escolha uma música",17,WHITE); playing.setSingleLine();
    playing.setEllipsize(android.text.TextUtils.TruncateAt.END); bottom.addView(playing);
    clock=label("0:00 / 0:00",12,0xffabbecd); bottom.addView(clock);
    progress=new SeekBar(this); progress.setMax(1);
    bottom.addView(progress);
    progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar s,int p,boolean fromUser){if(fromUser)clock.setText(time(p));}
      public void onStartTrackingTouch(SeekBar s){drag=true;}
      public void onStopTrackingTouch(SeekBar s){
        drag=false;if(player!=null&&!preparing)try{player.seekTo(s.getProgress());}catch(Exception ignored){}
      }
    });
    LinearLayout controls=new LinearLayout(this); controls.setGravity(Gravity.CENTER);
    Button prev=button("⏮"); pauseButton=button("▶"); Button next=button("⏭");
    controls.addView(prev,new LinearLayout.LayoutParams(0,dp(55),1));
    LinearLayout.LayoutParams big=new LinearLayout.LayoutParams(0,dp(55),1);
    big.leftMargin=dp(6);big.rightMargin=dp(6);
    pauseButton.setTextColor(GREEN); controls.addView(pauseButton,big);
    controls.addView(next,new LinearLayout.LayoutParams(0,dp(55),1));
    bottom.addView(controls);
    prev.setOnClickListener(v->skip(-1));
    next.setOnClickListener(v->skip(1));
    pauseButton.setOnClickListener(v->toggle());
    Button refresh=button("Atualizar músicas");
    refresh.setTextSize(14); bottom.addView(refresh,space(7));
    refresh.setOnClickListener(v->checkAndLoad());
    handler.post(ticker);
    checkAndLoad();
  }
  private static String time(long ms) {
    long seconds=Math.max(0,ms)/1000;
    return String.format(Locale.getDefault(),"%d:%02d",seconds/60,seconds%60);
  }
  private String permission() {
    return Build.VERSION.SDK_INT>=33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
  }
  private void checkAndLoad() {
    if (checkSelfPermission(permission())==PackageManager.PERMISSION_GRANTED) scan();
    else {
      status.setText("Permita acesso às músicas para carregar sua playlist.");
      requestPermissions(new String[]{permission()},PERMISSION);
    }
  }
  @Override public void onRequestPermissionsResult(int request,String[] perms,int[] grants) {
    super.onRequestPermissionsResult(request,perms,grants);
    if(request==PERMISSION) {
      if(checkSelfPermission(permission())==PackageManager.PERMISSION_GRANTED) scan();
      else status.setText("Permissão negada. Toque em Atualizar músicas para tentar novamente.");
    }
  }
  private void scan() {
    final int token=++scanId; status.setText("Buscando músicas no dispositivo...");
    new Thread(()->{
      ArrayList<Track> found=new ArrayList<>();
      String[] fields={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.DURATION};
      try(Cursor c=getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
             fields,null,null,MediaStore.Audio.Media.TITLE+" ASC")){
        if(c!=null)while(c.moveToNext()){
          long duration=c.getLong(3);
          if(duration>0)found.add(new Track(c.getLong(0),c.getString(1),c.getString(2),duration));
        }
      }catch(Exception ex){handler.post(()->status.setText("Erro ao acessar músicas: "+ex.getClass().getSimpleName()));}
      handler.post(()->{
        if(isFinishing()||isDestroyed()||token!=scanId)return;
        library.clear();library.addAll(found);loaded=true;
        status.setText(found.size()+" músicas encontradas · Playlist automática");
        filter(search.getText().toString());
      });
    },"MediaStoreScanner").start();
  }
  private void filter(String text) {
    if(adapter==null)return;
    String filter=text.toLowerCase(Locale.ROOT).trim();
    shown.clear();shownNames.clear();
    for(Track track:library) {
      if(track.name.toLowerCase(Locale.ROOT).contains(filter)
        ||track.artist.toLowerCase(Locale.ROOT).contains(filter)) {
        shown.add(track);shownNames.add("♫  "+track.name+"\n"+track.artist);
      }
    }
    adapter.notifyDataSetChanged();
  }
  private void playId(long id) {
    for(int i=0;i<library.size();i++)if(library.get(i).id==id){play(i);return;}
  }
  private void play(int idx) {
    if(idx<0||idx>=library.size())return;
    current=idx; wantPlay=true; preparing=true;
    if(player!=null){player.release();player=null;}
    Track t=library.get(current);
    playing.setText("♫  "+t.name+" — "+t.artist);
    pauseButton.setText("Ⅱ");
    MediaPlayer p=new MediaPlayer(); player=p;
    p.setAudioAttributes(new AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
    p.setOnPreparedListener(mp->{
      if(player!=mp)return;
      preparing=false;progress.setMax(Math.max(1,mp.getDuration()));
      if(wantPlay)mp.start();
      pauseButton.setText(wantPlay?"Ⅱ":"▶");
    });
    p.setOnCompletionListener(mp->{if(player==mp)skip(1);});
    p.setOnErrorListener((mp,what,extra)->{
      if(player==mp){preparing=false;wantPlay=false;pauseButton.setText("▶");}
      return true;
    });
    try{p.setDataSource(this,t.uri);p.prepareAsync();}
    catch(Exception e){preparing=false;wantPlay=false;pauseButton.setText("▶");status.setText("Não foi possível reproduzir "+t.name);}
  }
  private void toggle(){
    if(player==null){if(!library.isEmpty())play(current<0?0:current);return;}
    if(preparing){wantPlay=!wantPlay;pauseButton.setText(wantPlay?"Ⅱ":"▶");return;}
    try {
      if(player.isPlaying()){player.pause();pauseButton.setText("▶");}
      else{player.start();pauseButton.setText("Ⅱ");}
    }catch(Exception ignored){}
  }
  private void skip(int direction){
    if(library.isEmpty())return;
    play(current<0?0:(current+direction+library.size())%library.size());
  }
  @Override protected void onDestroy(){
    ++scanId;handler.removeCallbacksAndMessages(null);
    if(player!=null){player.release();player=null;}
    super.onDestroy();
  }
}
