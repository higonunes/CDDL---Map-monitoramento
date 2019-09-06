package com.example.myapplication;


import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.apache.commons.lang3.StringUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import br.ufma.lsdi.cddl.CDDL;
import br.ufma.lsdi.cddl.Connection;
import br.ufma.lsdi.cddl.ConnectionFactory;
import br.ufma.lsdi.cddl.listeners.ISubscriberListener;
import br.ufma.lsdi.cddl.message.Message;
import br.ufma.lsdi.cddl.pubsub.Subscriber;
import br.ufma.lsdi.cddl.pubsub.SubscriberFactory;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private CDDL cddl;
    private Subscriber subscriber;
    private String ID = "movimento";
    private String mensagemRecebida = "";
    EventBus eb;
    private Polyline polyline;
    private Marker marker;
    List<PointColor> listaPontos = new ArrayList<>();
    private Connection connectionServer;
    private TextView tvAtividade;
    private Boolean moveCamera = false, parado = false, contando = false;
    private long inicioParada = 0, fimParada = 0;
    private Double atividade, latitude, longitude, atividadeOLD, latitudeOLD, longitudeOLD;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        tvAtividade = (TextView) findViewById(R.id.atividade);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        eb = EventBus.builder().build();
        eb.register(this);

        configCDDL();
        // coletarDados();
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        //       LatLng sydney = new LatLng(-2.512078, -44.2325318);
//        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
//        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
//        mMap.animateCamera(CameraUpdateFactory.zoomTo(19));
    }

    private void configCDDL() {

        String host = "postman.cloudmqtt.com";
        connectionServer = ConnectionFactory.createConnection();
        connectionServer.setHost(host);
        connectionServer.setUsername("oirdlgbr");
        connectionServer.setPassword("iNlLp3WtIaU6");
        connectionServer.setPort("13905");
        connectionServer.setClientId(ID);
        connectionServer.connect();

        cddl = CDDL.getInstance();
        cddl.setConnection(connectionServer);
        cddl.setContext(this);

        cddl.startService();
        cddl.startCommunicationTechnology(CDDL.INTERNAL_TECHNOLOGY_ID);

        subscriber = SubscriberFactory.createSubscriber();
        subscriber.addConnection(connectionServer);
        subscriber.subscribeServiceByPublisherId(ID);
        subscriber.setSubscriberListener(new ISubscriberListener() {
            @Override
            public void onMessageArrived(Message message) {
                MapsActivity.this.onMessage(message);
            }
        });

    }

    private void onMessage(Message message) {
        eb.post(new MessageEvent(message));

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void on(MessageEvent event) {
        Object[] valor = event.getMessage().getServiceValue();
        atividadeOLD = atividade;
        latitudeOLD = latitude;
        longitudeOLD = longitude;

        mensagemRecebida = StringUtils.join(valor, ", ");
        String[] separated = mensagemRecebida.split(",");
        atividade = Double.valueOf(separated[0]);
        latitude = Double.valueOf(separated[1]);
        longitude = Double.valueOf(separated[2]);
        desenharMapa();

        System.out.println(atividadeOLD + " " + atividade);
    }

    @Override
    protected void onDestroy() {
        eb.unregister(this);
        super.onDestroy();
    }

    private void desenharMapa() {
        if (!mensagemRecebida.isEmpty()) {
            System.out.println(latitude + "," + longitude);

            LatLng latLng = new LatLng(latitude, longitude);
            if (mMap != null) {
                if (polyline != null)
                    polyline.remove();
                if (!moveCamera) {
                    CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 19);
                    mMap.animateCamera(cameraUpdate);
                    moveCamera = true;
                }

                if (atividade == 0.0 && !parado) {
                    parado = true;
                    System.out.println("parado, " + atividadeOLD);
                    marker = mMap.addMarker(new MarkerOptions().position(latLng));
                    tvAtividade.setText("Parado");
                    if (!contando) {
                        inicioParada = System.currentTimeMillis();
                        contando = true;
                    }
                    if (atividadeOLD == 1.0) {
                        listaPontos.add(new PointColor(latLng, Color.GREEN));
                    } else if (atividadeOLD == 2.0){
                        listaPontos.add(new PointColor(latLng, Color.BLUE));
                    }
                } else if (atividade == 1.0) {
                    if (contando){
                        fimParada = System.currentTimeMillis();
                        contando = false;
                    }
                    parado = false;
                    System.out.println("andando");
                    listaPontos.add(new PointColor(latLng, Color.GREEN));
                    tvAtividade.setText("Andando");
                } else if (atividade == 2.0){
                    if (contando){
                        fimParada = System.currentTimeMillis();
                        contando = false;
                    }
                    parado = false;
                    System.out.println("correndo");
                    listaPontos.add(new PointColor(latLng, Color.BLUE));
                    tvAtividade.setText("Correndo");
                }

                if (!contando && inicioParada != 0 && fimParada != 0){
                    System.out.println("tempo de parada: " + ((fimParada-inicioParada)/1000));
                    marker.setTitle((fimParada-inicioParada)/1000+ " seg");
                    inicioParada = 0;
                    fimParada = 0;
                }

                if (listaPontos.size() > 1)
                    polyline = drawPolyline(listaPontos);
            }
        }
    }

    private Polyline drawPolyline(List<PointColor> listaPontos) {
        System.out.println("desenhando");
        //cria uma nova arraylist de pontos que será utilizada para desenhar a polyline
        List<LatLng> pontosParaSeremDesenhados = new ArrayList<>();
        //cria uma instância do tipo PointColor e ela recebe o primeiro ponto da lista
        PointColor pointColor = listaPontos.get(0);
        //cor atual recebe a cor do primeiro item da lista
        int corAtual = pointColor.cor;

        //adiciona o primeiro ponto (i = 0) na lista para desenhar
        pontosParaSeremDesenhados.add(pointColor.pontolatLng);
        int i =1;

        while (i < listaPontos.size()) {
            pointColor = listaPontos.get(i);

            // se a cor atual for a cor do ponto atual, apenas adiciona na lista as coordenadas do ponto atual
            if (pointColor.cor == corAtual) {
                pontosParaSeremDesenhados.add(pointColor.pontolatLng);
                // se a cor atual nao for igual a do ponto atual (o movimento mudou), desenha a linha com os
                //pontos que já tem, limpa a lista e inicia novamente, adicionando o ponto atual na lista
                // de pontos a serem desenhados
            } else {
                pontosParaSeremDesenhados.add(pointColor.pontolatLng);
                mMap.addPolyline(new PolylineOptions()
                        .addAll(pontosParaSeremDesenhados)
                        .color(corAtual)
                        .width(10));
                corAtual = pointColor.cor;
                pontosParaSeremDesenhados.clear();
                pontosParaSeremDesenhados.add(pointColor.pontolatLng);
            }

            i++;
        }

        //desenha os pontos que tiveram restado na lista
        Polyline polyline = mMap.addPolyline(new PolylineOptions()
                .addAll(pontosParaSeremDesenhados)
                .color(corAtual)
                .width(10));

        return polyline;

    }

}
