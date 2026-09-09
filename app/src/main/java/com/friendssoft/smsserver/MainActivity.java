package com.friendssoft.smsserver;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.widget.*;
import java.io.*;
import java.net.*;
import android.text.TextUtils;
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
    void startServer(){ try { int p=Integer.parseInt(port.getText().toString().trim()); if(p<1||p>65535) throw new IllegalArgumentException("Invalid port"); if(server!=null) server.shutdown(); server=new FriendsHttpServer(p); server.start(); String ip=getLocalIpv4(); status.setText("Server ONLINE\nhttp://"+ip+":"+p); } catch(Exception e){ status.setText("Error: "+e.getMessage()); } }

String getLocalIpv4(){ try { Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces(); String fallback=null; while(interfaces.hasMoreElements()){ NetworkInterface ni=interfaces.nextElement(); if(!ni.isUp()||ni.isLoopback()) continue; boolean wifi=ni.getName()!=null && (ni.getName().toLowerCase(Locale.US).contains("wlan") || ni.getName().toLowerCase(Locale.US).contains("wifi")); Enumeration<InetAddress> addrs=ni.getInetAddresses(); while(addrs.hasMoreElements()){ InetAddress a=addrs.nextElement(); if(a instanceof Inet4Address && !a.isLoopbackAddress()){ String ip=a.getHostAddress(); if(wifi) return ip; if(fallback==null) fallback=ip; } } } if(fallback!=null) return fallback; } catch(Exception ignored){} return "127.0.0.1"; }
    void stopServer(){ if(server!=null) server.shutdown(); server=null; status.setText("Server stopped"); }
    @Override protected void onDestroy(){ if(server!=null) server.shutdown(); super.onDestroy(); }

    class FriendsHttpServer extends Thread {
        final int port; volatile boolean running=true; ServerSocket socket;
        FriendsHttpServer(int p){port=p;}
        public void run(){ try { socket=new ServerSocket(port); while(running){ Socket c=socket.accept(); new Thread(()->handle(c)).start(); } } catch(Exception ignored){} }
        void shutdown(){running=false; try{if(socket!=null)socket.close();}catch(Exception ignored){}}
        void handle(Socket c){ BufferedReader in=null; try { c.setSoTimeout(8000); in=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8)); String req=in.readLine(); if(req==null)return; String[] a=req.split(" "); String method=a[0], path=a[1]; int len=0; String line; while((line=in.readLine())!=null&&!line.isEmpty()){ if(line.toLowerCase(Locale.US).startsWith("content-length:")) len=Integer.parseInt(line.substring(15).trim()); }
                char[] buf=new char[Math.max(0,len)]; int off=0; while(off<buf.length){int n=in.read(buf,off,buf.length-off);if(n<0)break;off+=n;} String body=new String(buf);
                String out;
                if(path.equals("/")||path.equals("/index.html")) out="<html><body><h1>FriendsSoft SMS Server</h1><p>Server is online.</p><p>POST /send-message</p></body></html>";
                else if(path.startsWith("/v1/device/status")) out="{\"status\":\"online\",\"server\":\"FriendsSoft SMS Server\",\"port\":"+port+"}";
                else if(path.startsWith("/v1/thread")) out="[]";
                else if(method.equalsIgnoreCase("POST") && (path.equals("/send-message")||path.equals("/v1/sms")||path.equals("/sms"))) out=sendSms(body);
                else { write(c,404,"{\"error\":\"resource not found\"}","application/json"); return; }
                write(c,200,out,path.equals("/")||path.endsWith("html")?"text/html":"application/json");
            }catch(Exception e){ try{write(c,500,"{\"error\":\""+esc(e.getMessage())+"\"}","application/json");}catch(Exception ignored){} } finally { try{ if(in!=null) in.close(); }catch(Exception ignored){} try{ c.close(); }catch(Exception ignored){} } }
        String sendSms(String b){ String number=getJson(b,"number"); if(number.isEmpty()) number=getJson(b,"phone"); String msg=getJson(b,"message"); if(number.isEmpty()||msg.isEmpty()) return "{\"status\":false,\"error\":\"number and message are required\"}"; try{ SmsManager sm=SmsManager.getDefault(); ArrayList<String> parts=sm.divideMessage(msg); if(parts.size()>1) sm.sendMultipartTextMessage(number,null,parts,null,null); else sm.sendTextMessage(number,null,msg,null,null); return "{\"status\":true,\"sent\":true,\"number\":\""+esc(number)+"\"}"; }catch(Exception e){return "{\"status\":false,\"sent\":false,\"error\":\""+esc(e.getMessage())+"\"}";} }
        String getJson(String s,String key){ Pattern p=Pattern.compile("\\\""+Pattern.quote(key)+"\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"",Pattern.CASE_INSENSITIVE); Matcher m=p.matcher(s); return m.find()?m.group(1):""; }
        String esc(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
        void write(Socket c,int code,String body,String type)throws Exception{byte[] d=body.getBytes(StandardCharsets.UTF_8); OutputStream o=c.getOutputStream(); String h="HTTP/1.1 "+code+" "+(code==200?"OK":"Error")+"\r\nContent-Type: "+type+"; charset=utf-8\r\nContent-Length: "+d.length+"\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n";o.write(h.getBytes(StandardCharsets.UTF_8));o.write(d);o.flush();}
    }
}
