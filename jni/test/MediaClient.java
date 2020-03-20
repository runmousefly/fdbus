/*
 * Copyright (C) 2015   Jeremy Chen jeremy_cz@yahoo.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import ipc.fdbus.FdbusClient;
import ipc.fdbus.FdbusClientListener;
import ipc.fdbus.SubscribeItem;
import ipc.fdbus.Fdbus;
import ipc.fdbus.FdbusMessage;
import ipc.fdbus.NFdbExample;
import ipc.fdbus.Example.FdbusProtoBuilder;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import ipc.fdbus.Example.CPerson;
import ipc.fdbus.FdbusDeserializer;
import ipc.fdbus.FdbusParcelable;
import ipc.fdbus.FdbusParcelable.TextFormatter;

public class MediaClient 
{
    private class FdbusMediaClient extends TimerTask implements FdbusClientListener
    {
        public final static int CONFIG_SYNC_INVOKE = 1;

        int mSongId;
        
        private FdbusClient mClient;
        
        public FdbusMediaClient(String name)
        {
            mClient = new FdbusClient(name);
            mSongId = 0;
        }

        public FdbusClient client()
        {
            return mClient;
        }
        
        public void onOnline(int sid)
        {
            System.out.println(mClient.endpointName() + ": onOnline is received.");
            ArrayList<SubscribeItem> subscribe_items = new ArrayList<SubscribeItem>();
            subscribe_items.add(new SubscribeItem(NFdbExample.FdbMediaSvcMsgId.NTF_ELAPSE_TIME_VALUE, "my_filter"));
            subscribe_items.add(new SubscribeItem(NFdbExample.FdbMediaSvcMsgId.NTF_ELAPSE_TIME_VALUE, "raw_buffer"));
            mClient.subscribe(subscribe_items);
        }
        public void onOffline(int sid)
        {
            System.out.println(mClient.endpointName() + ": onOffline is received.");
        }

        private void handleReplyMsg(FdbusMessage msg, boolean sync)
        {
            if (msg.returnValue() != Fdbus.FDB_ST_OK)
            {
                System.out.println(mClient.endpointName() + 
                        ": Error is received at onReply. Error code: " + msg.returnValue());
                return;
            }

            switch (msg.code())
            {
                case NFdbExample.FdbMediaSvcMsgId.REQ_METADATA_VALUE:
                {
                    try {
                        NFdbExample.NowPlayingDetails np =
                                NFdbExample.NowPlayingDetails.parseFrom(msg.byteArray());
                        System.out.println((sync ? "sync reply - " : "async reply - ") + 
                                           mClient.endpointName() +
                                           ": now playing info: " +
                                           np.toString());
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                }
                break;
                case NFdbExample.FdbMediaSvcMsgId.REQ_RAWDATA_VALUE:
                {
                    FdbusDeserializer deserializer = new FdbusDeserializer(msg.byteArray());
                    CPerson[] persons = deserializer.out(new CPerson[deserializer.arrayLength()],
                                                            CPerson.class);
                    TextFormatter fmter = new TextFormatter();
                    fmter.format(persons);
                    System.out.println(fmter.stream());
                }
                break;
                default:
                break;
            }
        }
        
        public void onReply(FdbusMessage msg)
        {
            Object ud = msg.userData();
            if (ud != null)
            {
                if (ud instanceof ArrayList<?>)
                {
                    if (((ArrayList<?>)ud).get(0) instanceof String)
                    {
                        ArrayList<?> user_data = (ArrayList<?>)ud;
                        System.out.println("OnReply: user data is received with size " + user_data.size());
                    }
                }
            }
            handleReplyMsg(msg, false);
        }
        
        public void onBroadcast(FdbusMessage msg)
        {
            switch (msg.code())
            {
                case NFdbExample.FdbMediaSvcMsgId.NTF_ELAPSE_TIME_VALUE:
                {
                    if (msg.topic().equals("my_filter"))
                    {
                        try {
                            NFdbExample.ElapseTime et =
                                    NFdbExample.ElapseTime.parseFrom(msg.byteArray());
                            System.out.println(mClient.endpointName() + 
                                    ": elapse time is received: " + et.toString());
                        } catch (Exception e) {
                            System.out.println(e);
                        }
                    }
                }
                break;
                default:
                break;
            }
        }

        public void run()
        {
            NFdbExample.SongId.Builder proto_builder = NFdbExample.SongId.newBuilder();
            proto_builder.setId(mSongId);
            NFdbExample.SongId song_id = proto_builder.build();
            FdbusProtoBuilder builder = new FdbusProtoBuilder(song_id);

            ArrayList<String> usr_data = new ArrayList<String>();
            if (CONFIG_SYNC_INVOKE == 0)
            {
                for (int i = 0; i < 10000; ++i)
                {
                    usr_data.add(new String("a quick fox dump over brown dog " + i));
                }
                mClient.invokeAsync(NFdbExample.FdbMediaSvcMsgId.REQ_METADATA_VALUE, builder, usr_data, 0);
            }
            else
            {
                
                FdbusMessage msg = mClient.invokeSync(NFdbExample.FdbMediaSvcMsgId.REQ_METADATA_VALUE, builder, 0);
                handleReplyMsg(msg, true);
            }
            mClient.invokeAsync(NFdbExample.FdbMediaSvcMsgId.REQ_RAWDATA_VALUE, null, null, 0);
        }
    }

    Timer mTimer;
    public void startServerInvoker(TimerTask tt)
    {
        mTimer = new Timer();
        mTimer.schedule(tt, 500, 500);
    }

    private FdbusMediaClient createClient(String name)
    {
        FdbusMediaClient clt = new FdbusMediaClient(name);
        clt.client().setListener(clt);
        return clt;
    }
    
    public static void main(String[] args)
    {
        Fdbus fdbus = new Fdbus();
        MediaClient clt = new MediaClient();

        ArrayList<FdbusMediaClient> clients = new ArrayList<FdbusMediaClient>();
        for (String arg : args)
        {
            FdbusMediaClient client = clt.createClient(arg + "_client");
            client.client().connect("svc://" + arg);
            clt.startServerInvoker(client);

            clients.add(client);
        }
        
        try{Thread.sleep(500000);}catch(InterruptedException e){System.out.println(e);}
        //Thread.sleep(5000000);
    }
}
