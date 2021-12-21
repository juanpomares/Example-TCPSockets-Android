package es.fempa.juanpomares.examplesocketstcp;


import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity
{
    private TextView mTV;
    private Button mBtnClient, mBtnServer;
    private EditText mIPServer;

    private Socket mSocket;
    private ServerSocket mServerSocket;
    private boolean mConnectionEstablished;

    private DataInputStream mDataInputStream;
    private DataOutputStream mDataOutputStream;

    private int mPuertoClient=6000;
    private int mPuertoServer=4000;

    //Hilo para escuchar los mensajes lleguen por el socket
    //Thread used to read messages from socket
    private GetMessagesThread mListeningThread;


    //Hilo donde el servidor estará esperando a un cliente
    //Thread used by the server to wait a client
    private WaitingClientThread mWaitingThread;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBtnClient =(Button)findViewById(R.id.btnClient);
        mBtnServer =(Button)findViewById(R.id.btnServer);
        mIPServer =(EditText) findViewById(R.id.etIPServer);

        mTV =(TextView) findViewById(R.id.tvOutput);
    }

    public void startServer(View v)
    {
        setInterfaceEnabled(false);

        setText("\n"+getString(R.string.startServer));
        (mWaitingThread =new WaitingClientThread()).start();
    }

    private void setInterfaceEnabled(boolean value)
    {
        mBtnClient.setEnabled(value);
        mBtnServer.setEnabled(value);
        mIPServer.setEnabled(value);
    }

    public void startClient(View v)
    {
        String TheIP= mIPServer.getText().toString();
        if(TheIP.length()>5)
            //Se comprueba que haya algo de texto
            //Check if there are some text
        {
            setInterfaceEnabled(false);

            (new ClientConnectToServer(TheIP)).start();

            setText("\n"+getString(R.string.startClient));
            appendText("\n"+getString(R.string.clientTryingConnection)+TheIP);
        }
    }

    public void appendText(String text)
    {
        runOnUiThread(new AppendUITextView(text+"\n", mTV));
    }

    public void setText(String text)
    {
        runOnUiThread(new SetUITextView(text, mTV));
    }

    private class WaitingClientThread extends Thread
    {
        public void run()
        {
            setText(getString(R.string.waitingClient));
            try
            {
                //Abrimos el socket
                //Start the Server
                mServerSocket = new ServerSocket(mPuertoServer);

                //Mostramos un mensaje para indicar que estamos esperando en la direccion ip y el puerto...
                //Showing information message about IP addres and port
                appendText(getString(R.string.serverCreated)+"\n"+
                        getString(R.string.address)+getIpAddress()+" "+getString(R.string.port)+": "+ mServerSocket.getLocalPort());

                //Esperamos conexión de cliente
                //Waiting client connection
                mSocket = mServerSocket.accept();

                //Una vez hay conexion con un cliente, creamos los streams de salida/entrada
                //Whe connection established, let's create the communication streams

                mDataInputStream = new DataInputStream(mSocket.getInputStream());
                mDataOutputStream = new DataOutputStream(mSocket.getOutputStream());


                mConnectionEstablished =true;

                //Iniciamos el hilo para la escucha y procesado de mensajes
                //Creating listening message thread
                (mListeningThread =new GetMessagesThread()).start();

                //Enviamos mensajes desde el servidor.
                //Creating sent messages thread
                (new SendServerMessages()).start();
                mWaitingThread =null;
            }
            catch (Exception e)
            {
                e.printStackTrace();
                appendText(getString(R.string.problem)+": "+e.getMessage());

                runOnUiThread(() -> setInterfaceEnabled(true));
            }
        }
    }

    private class ClientConnectToServer extends Thread
    {
        String mIp;
        public ClientConnectToServer(String ip){mIp=ip;}
        public void run()
        {
            try {
                //Mostramos por la interfaz que nos vamos a conectar al servidor
                //Showing message informing connection is being created
                setText(getString(R.string.connectingServer)+": " + mIp + ":" + mPuertoClient+ "...\n\n");

                //Creamos el socket
                //Creating socket
                mSocket = new Socket(mIp, mPuertoClient);

                mDataInputStream = new DataInputStream(mSocket.getInputStream());
                mDataOutputStream = new DataOutputStream(mSocket.getOutputStream());


                mConnectionEstablished =true;
                //Iniciamos el hilo para la escucha y procesado de mensajes
                //Starting listeninsg socket thread
                (mListeningThread =new GetMessagesThread()).start();

                new SendClientMessages().start();

            }catch (Exception e)
            {
                e.printStackTrace();
                appendText(getString(R.string.problem)+": "+e.getMessage());

                runOnUiThread(() -> setInterfaceEnabled(true));
            }

        }

    }

    private class SendServerMessages extends Thread
    {
        public void run()
        {
            String[] messages=getResources().getStringArray(R.array.ServerMessages);
            int[] sleepTime ={1000, 2000, 2000};
            sendVariousMessages(messages, sleepTime);
            DisconnectSockets();
        }
    }

    private class SendClientMessages extends Thread
    {
        public void run()
        {
            String[] messages=getResources().getStringArray(R.array.ClientMessages);
            int[] sleepTime={1000, 2000, 1000};
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sendVariousMessages(messages, sleepTime);
            DisconnectSockets();
        }
    }

    private void DisconnectSockets()
    {
        if(mConnectionEstablished)
        {
            runOnUiThread(() -> setInterfaceEnabled(true));
            mConnectionEstablished = false;

            if (mListeningThread != null)
            {
                mListeningThread.setExecuting(false);
                mListeningThread.interrupt();
                mWaitingThread = null;
            }

            try {
                if (mDataInputStream != null)
                    mDataInputStream.close();
            } catch (Exception e) {
            } finally {
                mDataInputStream = null;
                try {
                    if (mDataOutputStream != null)
                        mDataOutputStream.close();
                } catch (Exception e) {
                } finally {
                    mDataOutputStream = null;
                    try {
                        if (mSocket != null)
                            mSocket.close();
                    } catch (Exception e) {
                    } finally {
                        mSocket = null;
                    }
                }
            }
        }
    }

    private void sendVariousMessages(String[] msgs, int[] time)
    {
        if(msgs!=null && time!=null && msgs.length==time.length)
            for(int i=0; i<msgs.length; i++)
            {
                sendMessage(msgs[i]);
                try {
                    Thread.sleep(time[i]);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
            }
    }

    private void sendMessage(String txt)
    {
        new SendMessageSocketThread(txt).start();
    }

    private class SendMessageSocketThread extends Thread
    {
        private String msg;

        SendMessageSocketThread(String message)
        {
            msg=message;
        }

        @Override
        public void run()
        {
            try
            {
                //Sending the message
                //Enviamos el mensaje
                mDataOutputStream.writeUTF(msg);
                appendText(getString(R.string.sended)+": "+msg);
            }catch (IOException e)
            {
                e.printStackTrace();
                appendText(getString(R.string.problem)+": " + e.toString() + "\n");
            }
        }
    }

    //Obtenemos la IP de nuestro terminal
    //Getting the device IP
    private String getIpAddress()
    {
        String ip = "";
        try
        {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements())
            {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                while (enumInetAddress.hasMoreElements())
                {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress())
                    {
                        ip += getString(R.string.serverIP)+": " + inetAddress.getHostAddress() + "\n";
                    }

                }
            }
        } catch (SocketException e)
        {
            e.printStackTrace();
            ip += getString(R.string.problem)+" "+ e.toString() + "\n";
        }

        return ip;
    }

    private class GetMessagesThread extends Thread
    {
        public boolean mIsExecuting;
        private String mLineRead;

        public void run()
        {
            mIsExecuting =true;

            while(mIsExecuting)
            {
                mLineRead ="";
                //Obtenemos la cadena del buffer
                //Get the string from socket stream
                mLineRead = getReadLine();

                //Comprobamos que esa cadena tenga contenido
                //Check if the string has some content
                if(mLineRead!=null && !mLineRead.equals("") && mLineRead.length()!=0)
                    //Añadimos texto recibido a la interfaz
                    //Recevied text added to TextView
                    appendText(getString(R.string.received)+": "+ mLineRead);
            }
        }

        public void setExecuting(boolean execute){
            mIsExecuting =execute;}


        private String getReadLine()
        {
            String cadena="";

            try {
                cadena= mDataInputStream.readUTF();//Leemos del datainputStream una cadena UTF
                Log.d("GetMessage", "Message received: "+cadena);

            }catch(Exception e)
            {
                e.printStackTrace();
                mIsExecuting =false;
            }
            return cadena;
        }
    }

    protected class SetUITextView implements Runnable
    {
        private String text;
        private TextView mTextView;
        public SetUITextView(String text, TextView tv){this.text=text; this.mTextView=tv;}
        public void run(){
            this.mTextView.setText(this.text);}
    }

    protected class AppendUITextView implements Runnable
    {
        private String text;
        private TextView mTextView;
        public AppendUITextView(String text, TextView tv){this.text=text; this.mTextView=tv;}
        public void run(){
            mTextView.append(this.text);}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DisconnectSockets();
    }
}
