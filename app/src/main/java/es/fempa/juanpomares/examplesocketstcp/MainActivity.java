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
    private Button mBtnCliente, mBtnServidor;
    private EditText mIPServer;

    private Socket mSocket;
    private ServerSocket mServerSocket;
    private boolean mConectionEstablished;

    private DataInputStream mDataInputStream;
    private DataOutputStream mDataOutputStream;

    private int mPuertoClient=6000;
    private int mPuertoServer=4000;

    //Hilo para escuchar los mensajes lleguen por el socket
    private GetMessagesThread mListeningThread;


    /*Hilo donde el servidor estará esperando a un cliente*/
    private WaitingClientThread mWaitingThread;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBtnCliente =(Button)findViewById(R.id.buttonCliente);
        mBtnServidor =(Button)findViewById(R.id.buttonServer);
        mIPServer =(EditText) findViewById(R.id.ipServer);

        mTV =(TextView) findViewById(R.id.tvSalida);
    }

    public void startServer(View v)
    {
        mBtnCliente.setEnabled(false);
        mBtnServidor.setEnabled(false);
        mIPServer.setEnabled(false);

        setText("\nComenzamos Servidor!");
        (mWaitingThread =new WaitingClientThread()).start();
    }

    public void startClient(View v)
    {
        String TheIP= mIPServer.getText().toString();
        if(TheIP.length()>5)//Se comprueba que haya algo de texto
        {
            mBtnCliente.setEnabled(false);
            mBtnServidor.setEnabled(false);
            mIPServer.setEnabled(false);

            (new ClientConnectToServer(TheIP)).start();

            setText("\nComenzamos Cliente!");
            appendText("\nNos intentamos conectar al servidor: "+TheIP);
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
            setText("Esperando Usuario...");
            try
            {
                //Abrimos el socket
                mServerSocket = new ServerSocket(mPuertoServer);

                //Mostramos un mensaje para indicar que estamos esperando en la direccion ip y el puerto...
                appendText("Creado el servidor\n Dirección: "+getIpAddress()+" Puerto: "+ mServerSocket.getLocalPort());

                //Creamos un socket que esta a la espera de una conexion de cliente
                mSocket = mServerSocket.accept();

                //Una vez hay conexion con un cliente, creamos los streams de salida/entrada
                try {
                    mDataInputStream = new DataInputStream(mSocket.getInputStream());
                    mDataOutputStream = new DataOutputStream(mSocket.getOutputStream());
                }catch(Exception e){ e.printStackTrace();}

                mConectionEstablished =true;

                //Iniciamos el hilo para la escucha y procesado de mensajes
                (mListeningThread =new GetMessagesThread()).start();

                //Enviamos mensajes desde el servidor.
                (new EnvioMensajesServidor()).start();
                mWaitingThread =null;
            }
            catch (IOException e)
            {
                e.printStackTrace();
                appendText("Ha habido un problema: "+e.getMessage());
            }
        }
    }

    private class ClientConnectToServer extends Thread
    {
        String mIp;
        public ClientConnectToServer(String ip){mIp=ip;}
        public void run()
        {
            //TODO Connect to server
            try {
                setText("Conectando con el servidor: " + mIp + ":" + mPuertoClient+ "...\n\n");//Mostramos por la interfaz que nos hemos conectado al servidor} catch (IOException e) {

                mSocket = new Socket(mIp, mPuertoClient);//Creamos el socket

                try {
                    mDataInputStream = new DataInputStream(mSocket.getInputStream());
                    mDataOutputStream = new DataOutputStream(mSocket.getOutputStream());
                }catch(Exception e){ e.printStackTrace();}

                mConectionEstablished =true;
                //Iniciamos el hilo para la escucha y procesado de mensajes
                (mListeningThread =new GetMessagesThread()).start();

                new EnvioMensajesCliente().start();

            } catch (Exception e) {
                e.printStackTrace();
                appendText("Error: " + e.getMessage());
            }
        }

    }

    private class EnvioMensajesServidor extends Thread
    {
        public void run()
        {
            String messages[]={"Bienvenido usuario a mi chat", "¿Estás bien?", "Bueno, pues molt bé, pues adiós"};
            int sleeptime[]={1000, 2000, 2000};
            sendVariousMessages(messages, sleeptime);
            DisconnectSockets();
        }
    }

    private class EnvioMensajesCliente extends Thread
    {
        public void run()
        {
            String messages[]={"Hola servidor", "No mucho, pero no te voy a contar mi vida", "Pues adiós :("};
            int sleeptime[]={1000, 2000, 1000};
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sendVariousMessages(messages, sleeptime);
            DisconnectSockets();
        }
    }

    private void DisconnectSockets()
    {
        if(mConectionEstablished)
        {
            runOnUiThread(new Runnable() {
                @Override
                public void run()
                {
                    mBtnCliente.setEnabled(true);
                    mBtnServidor.setEnabled(true);
                    mIPServer.setEnabled(true);
                }
            });
            mConectionEstablished = false;

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
                mDataOutputStream.writeUTF(msg);//Enviamos el mensaje
                appendText("Enviado: "+msg);
            }catch (IOException e)
            {
                e.printStackTrace();
                appendText("¡Algo fue mal! " + e.toString() + "\n");
            }
        }
    }

    //Obtenemos la IP de nuestro terminal
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
                        ip += "IP de Servidor: " + inetAddress.getHostAddress() + "\n";
                    }

                }
            }
        } catch (SocketException e)
        {
            e.printStackTrace();
            ip += "¡Algo fue mal! " + e.toString() + "\n";
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
                mLineRead = getReadLine();//Obtenemos la cadena del buffer
                if(mLineRead !="" && mLineRead.length()!=0)//Comprobamos que esa cadena tenga contenido
                    appendText("Recibido: "+ mLineRead);//Procesamos la cadena recibida
            }
        }

        public void setExecuting(boolean execute){
            mIsExecuting =execute;}


        private String getReadLine()
        {
            String cadena="";

            try {
                cadena= mDataInputStream.readUTF();//Leemos del datainputStream una cadena UTF
                Log.d("ObtenerCadena", "Cadena reibida: "+cadena);

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
