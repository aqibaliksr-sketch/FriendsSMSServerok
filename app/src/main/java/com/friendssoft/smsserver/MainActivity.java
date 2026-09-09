package com.friendssoft.smsserver;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    FriendsHttpServer server;
    TextView status; EditText port;
    @Override public void onCreate(Bundle b){ super.onCreate(b); setContentView(R.layout.activity_main);
        status=findViewById(R.id.status); port=findViewById(R.id.port);
        if(android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.SEND_SMS},10);
        findViewById(R.id.start).setOnClickListener(v->startServer());
        findViewById(R.id.stop).setOnClickListener(v->stopServer());
    }
    void startServer(){ try { int p=Integer.parseInt(port.getText().toString().trim()); if(server!=null) server.stop(); server=new FriendsHttpServer(p); server.start(); status.setText("Server ONLINE: http://0.0.0.0:"+p); } catch(Exception e){ status.setText("Error: "+e.getMessage()); } }
    void stopServer(){ if(server!=null) server.stop(); server=null; status.setText("Server stopped"); }
    @Override protected void onDestroy(){ if(server!=null) server.stop(); super.onDestroy(); }

    class FriendsHttpServer extends Thread {
        final int port; volatile boolean running=true; ServerSocket socket;
        FriendsHttpServer(int p){port=p;}
        public void run(){ try { socket=new ServerSocket(port); while(running){ Socket c=socket.accept(); new Thread(()->handle(c)).start(); } } catch(Exception ignored){} }
        void stop(){running=false; try{if(socket!=null)socket.close();}catch(Exception ignored){}}
        void handle(Socket c){
            try {
                c.setSoTimeout(10000);
                BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8));
                String req=in.readLine();
                if(req==null){ c.close(); return; }
                String[] a=req.split(" ");
                if(a.length<2){ write(c,400,"{\"error\":\"bad request\"}","application/json"); c.close(); return; }
                String method=a[0], path=a[1];
                int len=0; String line;
                while((line=in.readLine())!=null&&!line.isEmpty()){
                    if(line.toLowerCase(Locale.US).startsWith("content-length:")) len=Integer.parseInt(line.substring(15).trim());
                }
                if(method.equalsIgnoreCase("OPTIONS")){ writeOptions(c); c.close(); return; }
                char[] buf=new char[Math.max(0,len)]; int off=0;
                while(off<buf.length){ int n=in.read(buf,off,buf.length-off); if(n<0) break; off+=n; }
                String body=new String(buf);
                String out;
                if(path.equals("/")||path.equals("/index.html")) out="<html><body><h1>FriendsSoft SMS Server</h1><p>Server is online.</p><p>POST /v1/sms or /send-message</p></body></html>";
                else if(path.startsWith("/v1/device/status")) {
                    boolean allowed = android.os.Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.SEND_SMS)==PackageManager.PERMISSION_GRANTED;
                    out="{\"status\":\"online\",\"server\":\"FriendsSoft SMS Server\",\"port\":"+port+",\"smsPermission\":"+allowed+"}";
                }
                else if(path.startsWith("/v1/thread")) out="[]";
                else if(method.equalsIgnoreCase("POST") && (path.equals("/send-message")||path.equals("/v1/sms")||path.equals("/sms"))) out=sendSms(body);
                else { write(c,404,"{\"error\":\"resource not found\"}","application/json"); c.close(); return; }
                write(c,200,out,path.equals("/")||path.endsWith("html")?"text/html":"application/json");
            } catch(Exception e){ try{write(c,500,"{\"status\":false,\"sent\":false,\"error\":\""+esc(e.getMessage())+"\"}","application/json");}catch(Exception ignored){} }
            finally { try{c.close();}catch(Exception ignored){} }
        }
        String sendSms(String b){
            String number="", msg="";
            try {
                org.json.JSONObject o=new org.json.JSONObject(b==null?"":b);
                number=o.optString("number",o.optString("phone","")).trim();
                msg=o.optString("message","");
            } catch(Exception e) { return "{\"status\":false,\"sent\":false,\"error\":\"Invalid JSON request\"}"; }
            if(number.isEmpty()||msg.isEmpty()) return "{\"status\":false,\"sent\":false,\"error\":\"number and message are required\"}";
            if(android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED)
                return "{\"status\":false,\"sent\":false,\"error\":\"SEND_SMS permission is not granted\"}";
            try{
                String n=number.replaceAll("[^0-9+]","");
                if(n.startsWith("0") && n.length()==11) n="+92"+n.substring(1);
                else if(n.startsWith("92") && n.length()==12) n="+"+n;
                SmsManager sm=SmsManager.getDefault();
                ArrayList<String> parts=sm.divideMessage(msg);
                if(parts.size()>1) sm.sendMultipartTextMessage(n,null,parts,null,null);
                else sm.sendTextMessage(n,null,msg,null,null);
                return "{\"status\":true,\"sent\":true,\"number\":\""+esc(n)+"\"}";
            }catch(Exception e){return "{\"status\":false,\"sent\":false,\"error\":\""+esc(e.getMessage())+"\"}";}
        }
        String esc(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
        void writeOptions(Socket c)throws Exception{ OutputStream o=c.getOutputStream(); String h="HTTP/1.1 204 No Content\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: Content-Type\r\nAccess-Control-Allow-Private-Network: true\r\nAccess-Control-Max-Age: 86400\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"; o.write(h.getBytes(StandardCharsets.UTF_8)); o.flush(); }
        void write(Socket c,int code,String body,String type)throws Exception{byte[] d=body.getBytes(StandardCharsets.UTF_8); OutputStream o=c.getOutputStream(); String status=code==200?"OK":(code==404?"Not Found":"Error"); String h="HTTP/1.1 "+code+" "+status+"\r\nContent-Type: "+type+"; charset=utf-8\r\nContent-Length: "+d.length+"\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: Content-Type\r\nAccess-Control-Allow-Private-Network: true\r\nConnection: close\r\n\r\n";o.write(h.getBytes(StandardCharsets.UTF_8));o.write(d);o.flush();}
    }
}
