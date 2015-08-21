/**Copyright [2015] [SamSoft.es]

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 **/


package wear.samsoft.es.simpleinfoface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Clase encargada de manejar el servicio del reloj
 */
public class ServicioMain extends CanvasWatchFaceService {

    /**
     * el tiempo de actualizacion en milisegundos del modo interactivo,
     * lo actualizaremos una vez al segundo
     */
    private static final long INTERACTIVE_UPDATE = TimeUnit.SECONDS.toMillis(1);


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    /**
     * El motor del reloj, sera el encargado de realizar las actualizaciones
     * necesarias en los elementos ui
     */
    private class Engine extends CanvasWatchFaceService.Engine {
        //El id de mensaje para el manejador de la actualizacion de los segundos
        static final int MSG_UPDATE_TIME = 0;

        /**
         * hilo que monta un Handler para actualizar el modo interactivo.
         */
        final ThreadLocal<Handler> mUpdateTimeHandler = new ThreadLocal<Handler>() {
            @Override
            protected Handler initialValue() {
                return new Handler() {
                    @Override
                    public void handleMessage(Message message) {
                        switch (message.what) {
                            case MSG_UPDATE_TIME:
                                //refrescamos para que repinte
                                invalidate();
                                //preguntamos si estamos en modo interactivo
                                if (isVisible() && !isInAmbientMode()) {
                                    //actualizamos el tiempo
                                    long timeMs = System.currentTimeMillis();
                                    long delayMs = INTERACTIVE_UPDATE - (timeMs % INTERACTIVE_UPDATE);
                                    mUpdateTimeHandler.get().sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                                }
                                break;
                        }
                    }
                };
            }
        };

        /**
         * Metodo que inicia la actualizacion de los segundos si estamos en modo interactivo
         */
        private void updateTimer() {
            mUpdateTimeHandler.get().removeMessages(MSG_UPDATE_TIME);
            if (isVisible() && !isInAmbientMode()) {
                mUpdateTimeHandler.get().sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        //la hora local
        Calendar mCalendar = Calendar.getInstance(TimeZone.getTimeZone(TimeZone.getDefault().getID()));

        //control de los broadcast
        boolean mRegisteredTimeZoneReceiver = false;

        /**
         * broadcast para actualizar la zona horaria
         */
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getTimeZone(intent.getStringExtra("time-zone")));
            }
        };

        /**
         * broadcast para leer el nivel de bateria
         */
        final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                level= intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            }
        };

        //nivel de bateria que actualizaremos en el broadcast
        int level=-1;

        // Constantes para control son los radiantes
        final float TWO_PI = (float) Math.PI * 2f;//2f es la circunferencia entera
        final float PI = (float) Math.PI;

        //pinceles
        Paint mBackgroundPaint=new Paint();
        Paint pincelAzulBlur = new Paint();
        Paint mMinutePaint = new Paint();
        Paint pincelCyanSolido = new Paint();
        Paint mHourPaint = new Paint();
        Paint pincelBlancoSolido = new Paint();
        Paint pincelCasilleroFecha = new Paint();
        Paint pincelCasilleroBateria=new Paint();
        Paint pincelMarcadoresHoras=new Paint();
        Paint pincelMarcoBat=new Paint();
        Paint pincelInfoBat=new Paint();


        //indicador Ambient mode
        boolean mAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            //Seteamos el estilo de la caratula como los modos de tarjetas y si queremos mostrar
            //la hora del sistema
            setWatchFaceStyle(new WatchFaceStyle.Builder(ServicioMain.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            //seteamos la zona horaria
            mCalendar.setTimeZone(TimeZone.getDefault());



            //preparamos el fondo, en este caso no tiene imagen y el fondo es de color negro
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);

            //se utilizara para pintar los segundos
            pincelAzulBlur.setStyle(Paint.Style.STROKE);
            pincelAzulBlur.setColor(Color.CYAN);
            pincelAzulBlur.setAntiAlias(true);
            pincelAzulBlur.setMaskFilter(new BlurMaskFilter(10f, BlurMaskFilter.Blur.OUTER));
            pincelAzulBlur.setStrokeJoin(Paint.Join.ROUND);
            pincelAzulBlur.setStrokeWidth(15f);

            //se utilizara para pintar los minutos
            mMinutePaint.setColor(Color.WHITE);
            mMinutePaint.setStrokeWidth(10f);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setMaskFilter(new BlurMaskFilter(8f, BlurMaskFilter.Blur.SOLID));

            //se utilizara para pintar las horas
            mHourPaint.setColor(Color.CYAN);
            mHourPaint.setStrokeWidth(10f);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setMaskFilter(new BlurMaskFilter(8f, BlurMaskFilter.Blur.SOLID));

            //se utilizara para pintar la fecha
            pincelCyanSolido.setColor(Color.CYAN);
            pincelCyanSolido.setAntiAlias(true);
            pincelCyanSolido.setMaskFilter(new BlurMaskFilter(2f, BlurMaskFilter.Blur.SOLID));

            //se utilizara para pintar la fecha
            pincelBlancoSolido.setColor(Color.WHITE);
            pincelBlancoSolido.setAntiAlias(true);
            pincelBlancoSolido.setMaskFilter(new BlurMaskFilter(2f, BlurMaskFilter.Blur.SOLID));

            //se utilizara para pintar el marco de la fecha
            pincelCasilleroFecha.setStrokeWidth(3f);
            pincelCasilleroFecha.setAntiAlias(true);
            pincelCasilleroFecha.setStyle(Paint.Style.STROKE);
            pincelCasilleroFecha.setStrokeCap(Paint.Cap.ROUND);
            pincelCasilleroFecha.setMaskFilter(new BlurMaskFilter(2f, BlurMaskFilter.Blur.INNER));

            //se utilizara para el marco de la bateria
            pincelCasilleroBateria.setStrokeWidth(6f);
            pincelCasilleroBateria.setAntiAlias(true);
            pincelCasilleroBateria.setStyle(Paint.Style.STROKE);
            pincelCasilleroBateria.setStrokeCap(Paint.Cap.ROUND);
            pincelCasilleroBateria.setMaskFilter(new BlurMaskFilter(3f, BlurMaskFilter.Blur.SOLID));

            //se utilizara para pintar el marco de la bateria
            pincelMarcoBat.setStrokeWidth(2f);
            pincelMarcoBat.setAntiAlias(true);
            pincelMarcoBat.setColor(Color.WHITE);
            pincelMarcoBat.setStyle(Paint.Style.STROKE);
            pincelMarcoBat.setStrokeCap(Paint.Cap.ROUND);
            pincelMarcoBat.setMaskFilter(new BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL));

            //se utilizara para pintar la fecha
            pincelInfoBat.setColor(Color.WHITE);
            pincelInfoBat.setAntiAlias(true);
            pincelInfoBat.setMaskFilter(new BlurMaskFilter(2f, BlurMaskFilter.Blur.SOLID));
            pincelInfoBat.setTextSize(13f);

            //se utilizara para pintar los marcadores de las horas
            pincelMarcadoresHoras.setStrokeWidth(3f);
            pincelMarcadoresHoras.setAntiAlias(true);
            pincelMarcadoresHoras.setColor(Color.WHITE);
            pincelMarcadoresHoras.setStyle(Paint.Style.STROKE);
            pincelMarcadoresHoras.setStrokeCap(Paint.Cap.ROUND);
            pincelMarcadoresHoras.setMaskFilter(new BlurMaskFilter(2f, BlurMaskFilter.Blur.SOLID));

        }

        //variables que utilizaremos para pintar en el canvas
        //los centros
        float centerX;
        float centerY;
        //el tamaño
        int width;
        int height;
        //este parametro se usa para determinar las direcciones del efecto
        float pathEffectLen;
        //este es el efecto en si
        DashPathEffect dashPathEffect;
        //variables auxiliares
        float hrYAuxPrima;
        float hrXAuxPrima;
        float hrYAux;
        float hrXAux;
        float ahrsAux;
        //longitud de las manecillas
        float longHrs;
        float longMin;
        //los string que represntaran la fecha
        String fechaBlanca;
        String fechaCyan;
        //tamaño del casillero que contendra la fecha
        float casilleroWidth;
        float casilleroHeigth;
        //este es el casillero
        RectF casillero;
        //un degradado para el casillero
        LinearGradient shader;
        //los componentes de la bateria
        RectF casilleroBateriaPadre;
        RectF casilleroBateria;
        //tamaños del texto de bateria
        float htextoBat;
        float wtextoBat;
        //creamos el shader para aplicarselo
        Shader shaderBat;
        //angulo de inicio calculado que representa el nivel de bateria
        float abat;
        //angulo de final calculado que representa el nivel de bateria
        float finBat;
        //el offset para pintar la fecha cyan en X
        float offsetXTextoFechaCyan;
        //el offset para pintar la fecha blanca en X
        float offsetXTextoFecha;
        //el offset para pintar la fecha en Y
        float offsetYTextoFecha;
        //padding para el casillero de la fecha
        float paddingFecha=3f;
        //posiciones del texto fecha
        float xTextoBat;
        float yTextoBat;

        /*
        utilizamos este metodo para inicializar todos los componentes graficos para no hacerlo
        en el onDraw y no cargar el proceso dado que el onDraw se llama cada poco tiempo y
        es costoso en terminos de procesador tener que realizar todas las operaciones.
         */
        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int widthA, int heightA) {
            super.onSurfaceChanged(holder, format, widthA, heightA);


            //tomamos la hora
            mCalendar.setTimeInMillis(System.currentTimeMillis());

            //tomamos las medidas del canvas
            width = widthA;
            height = heightA;

            //tomamos el centro de la circunferencia
            centerX = width / 2f;
            centerY = height / 2f;

            //calculamos el angulo que tomaremos para cada segundo
            pathEffectLen = (PI * width) / 60f;
            //creamos los efectos para el casillero de los segundos
            dashPathEffect = new DashPathEffect(
                    new float[]{pathEffectLen - 6f, 4f},
                    0f);
            pincelAzulBlur.setPathEffect(dashPathEffect);

            //obtenemos las longitudes de la bateria
            wtextoBat=pincelInfoBat.measureText(String.valueOf(level)+"%");
            htextoBat=pincelInfoBat.ascent()+pincelCyanSolido.descent();

            if(heightA>=320){
                pincelCyanSolido.setTextSize(13f);
                pincelBlancoSolido.setTextSize(13f);
                //vamos a pintar dos casilleros, uno de ellos para indicar el nivel de bateria
                casilleroBateria=new RectF(
                        (centerX/2)-30,
                        centerY-60,
                        (centerX/2)+30,
                        centerY
                );

                //el otro es un marco
                casilleroBateriaPadre=new RectF(
                        (centerX/2)-40,
                        centerY-70,
                        (centerX/2)+40,
                        centerY+10
                );

                xTextoBat=(centerX/2)-(wtextoBat/2);
                yTextoBat=(centerY-30)-(htextoBat/2);

                //construimos la fecha
                fechaBlanca = mCalendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.ENGLISH)
                        + " " +
                        String.valueOf(Calendar.DAY_OF_MONTH)
                        + " ";
                fechaCyan = mCalendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.ENGLISH)
                        + " " +
                        String.valueOf(mCalendar.get(Calendar.YEAR));
            }
            else{
                pincelCyanSolido.setTextSize(12f);
                pincelBlancoSolido.setTextSize(12f);
                //vamos a pintar dos casilleros, uno de ellos para indicar el nivel de bateria
                casilleroBateria=new RectF(
                        (centerX/2)-20,
                        centerY-40,
                        (centerX/2)+20,
                        centerY
                );

                //el otro es un marco
                casilleroBateriaPadre=new RectF(
                        (centerX/2)-30,
                        centerY-50,
                        (centerX/2)+30,
                        centerY+10
                );

                xTextoBat=(centerX/2)-(wtextoBat/2);
                yTextoBat=(centerY-20)-(htextoBat/2);

                //construimos la fecha
                fechaBlanca = mCalendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.ENGLISH)
                        + " " +
                        String.valueOf(Calendar.DAY_OF_MONTH)
                        + " ";
                fechaCyan = mCalendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.ENGLISH);
            }

            //creamos el shader para aplicarselo
            shaderBat = new LinearGradient(
                    casilleroBateria.left-20,
                    casilleroBateria.top,
                    casilleroBateria.right-5,
                    casilleroBateria.top-20,
                    Color.GREEN,Color.RED, Shader.TileMode.CLAMP);
            pincelCasilleroBateria.setShader(shaderBat);

            //damos valor inicial al arco de la bateria
            abat = level / 100f * TWO_PI;
            finBat = (180 * abat) / PI;

            //calculamos los offsets de la fecha que esta dividida en dos para poder pintarlas en
            //dos colores
            offsetXTextoFecha = centerX + centerX/4;
            offsetYTextoFecha = centerY - centerY/6;
            offsetXTextoFechaCyan = offsetXTextoFecha + pincelBlancoSolido.measureText(fechaBlanca);

            //toammos las medidas
            casilleroWidth = pincelBlancoSolido.measureText(fechaBlanca)+
                    pincelCyanSolido.measureText(fechaCyan);
            casilleroHeigth = pincelBlancoSolido.ascent()-pincelBlancoSolido.descent();

            //calculamos el casillero que va a contener a la fecha
            casillero=new RectF(
                    offsetXTextoFecha - paddingFecha,
                    offsetYTextoFecha + paddingFecha ,
                    offsetXTextoFecha + casilleroWidth + paddingFecha,
                    offsetYTextoFecha + casilleroHeigth + paddingFecha
            );

            //creamos el shader para aplicarselo
            shader = new LinearGradient(casillero.left,casillero.top,
                    casillero.right,casillero.bottom,
                    Color.CYAN,Color.WHITE, Shader.TileMode.MIRROR);

            //seteamos el shader
            pincelCasilleroFecha.setShader(shader);

        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            //pintamos el fondo, en este caso un color negro
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Actualizamos el tiempo
            mCalendar.setTimeInMillis(System.currentTimeMillis());

            // Calculos de los angulos en radianes para mover los segundos, minutos y horas.
            //segundos
            float seconds = mCalendar.get(Calendar.SECOND) +
                    mCalendar.get(Calendar.MILLISECOND) / 1000f;
            //angulo de la porcion de cada segudno
            float asec = seconds / 60f * TWO_PI;
            //minutos
            float minutes = mCalendar.get(Calendar.MINUTE) + seconds / 60f;
            //angulo de la porcion de cada minuto
            float amin = minutes / 60f * TWO_PI;
            //horas
            float hours = mCalendar.get(Calendar.HOUR) + minutes / 60f;
            //angulo de la porcion de cada minuto
            float ahrs = hours / 12f * TWO_PI;


            // solo pintaremos cierta informacion si estamos en modo interactivo
            // para respetar al maximo la bateria
            if (!isInAmbientMode()) {

                for (int i = 1; i <= 12; i++) {
                    //pintamos los iindicadores de la hora, pero no aconsejo pintar esto aqui,
                    // si no ponerun fondo con las marcas de las horas ya puestas
                    //ya que así no cargamos el proceso de pintado con bucles

                    ahrsAux = i / 12f * TWO_PI;
                    hrXAux = (float) Math.sin(ahrsAux) * (width-centerX-15f);
                    hrYAux = (float) -Math.cos(ahrsAux) * (height-centerY-15f);
                    hrXAuxPrima = (float) Math.sin(ahrsAux) * (width-centerX-25f);
                    hrYAuxPrima = (float) -Math.cos(ahrsAux) * (height-centerY-25f);

                    canvas.drawLine(
                            centerX + hrXAux,
                            centerY + hrYAux,
                            centerX + hrXAuxPrima,
                            centerY + hrYAuxPrima,
                            pincelMarcadoresHoras
                    );
                }


                //preparamos to do lo necesario para para pintar los segundos
                final RectF ovalSec = new RectF();
                //calculamos el angulo de fin del arco
                float finSec = (180f * asec) / PI;
                //longitud del cuadrado
                float longSegundos=20f;
                //le damos medidas a la base del arco
                ovalSec.set(
                        bounds.left + longSegundos,
                        bounds.top + longSegundos,
                        bounds.right - longSegundos,
                        bounds.bottom - longSegundos
                );
                //pintamos eel arco desde -90 que es desde las 12 en adelante, no pintamos centro
                canvas.drawArc(ovalSec, -90f, finSec, false, pincelAzulBlur);


                //pintamos los dos trozos de la fecha por separado para poder cambiar su color
                canvas.drawText(fechaBlanca, offsetXTextoFecha,
                        offsetYTextoFecha, pincelBlancoSolido);
                //uno blanco y otro cyan
                canvas.drawText(fechaCyan, offsetXTextoFechaCyan,
                        offsetYTextoFecha, pincelCyanSolido);


                //pintamos el casillero de la fecha
                canvas.drawRect(casillero, pincelCasilleroFecha);

                //si hemos obtenido lectura de la bateria la mostraremos
                if (level!=-1){

                    //tomamos los angulos de la bateria
                    abat = level / 100f * TWO_PI;
                    finBat = (180f * abat) / PI;

                    //pintamos el marco de la bateria
                    canvas.drawArc(casilleroBateriaPadre,
                            0f,360f,
                            false,
                            pincelMarcoBat
                    );
                    //pintamos el nivel bateria
                    canvas.drawArc(casilleroBateria,
                            -90f,finBat,
                            false,
                            pincelCasilleroBateria
                    );
                    //pintamos el texto que va dentro
                    canvas.drawText(String.valueOf(level)+"%",xTextoBat,yTextoBat, pincelInfoBat);

                }
            }

            longMin = centerX - 40f;
            longHrs = centerX - 80f;

            // Pintamos las manecillas de las horas y los minutos.
            float minX = (float) Math.sin(amin) * longMin;
            float minY = (float) -Math.cos(amin) * longMin;
            canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY,
                    mMinutePaint);
            float hrX = (float) Math.sin(ahrs) * longHrs;
            float hrY = (float) -Math.cos(ahrs) * longHrs;
            canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY,
                    mHourPaint);
        }



        @Override
        public void onDestroy() {
            //deshabilitamos el handler para que deje de actualizar
            mUpdateTimeHandler.get().removeMessages(MSG_UPDATE_TIME);
            if(esRegistradoBat) {
                //desregistramos
                ServicioMain.this.unregisterReceiver(mBatteryReceiver);
                esRegistradoBat = false;
            }
            super.onDestroy();
        }


        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            //Si es visible
            if (visible) {
                registerReceiver();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeInMillis(System.currentTimeMillis());
                if(!esRegistradoBat) {
                    //registramos el broadcast de la bateria
                    IntentFilter filterCambioDeBateria = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    ServicioMain.this.registerReceiver(mBatteryReceiver, filterCambioDeBateria);
                    esRegistradoBat = true;
                }

            } else {
                unregisterReceiver();
                if(esRegistradoBat) {
                    ServicioMain.this.unregisterReceiver(mBatteryReceiver);
                    esRegistradoBat = false;
                }
            }
            //El reloj depende de si es visible o esta en ambient mode, por lo que tenemos que
            //controlar el inicio y la parada del reloj
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver ) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filterCambioZonaHoraria = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            ServicioMain.this.registerReceiver(mTimeZoneReceiver, filterCambioZonaHoraria);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver ) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            ServicioMain.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            //aqui cambiariamos los tamaños y posiciones si hiciera falta
            //dependiendo de si es redondo o cuadrado
            //en este caso nosotros nos hemos centrado en las resoluciones
            //y pintar igual todos los relojes

        }

        //metodo que se repite cada segundo
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            //repintamos cada segundo
            invalidate();
        }

        boolean esRegistradoBat=false;

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            //tomamos el valor para saber si estamos en modo ambiente
            mAmbient = inAmbientMode;
            //si es ambient quitaremos el color de la manecilla y el
            //antialias como sugiere google
            if (mAmbient) {

                if(esRegistradoBat) {
                    //desregistramos el broadcast  si procede
                    ServicioMain.this.unregisterReceiver(mBatteryReceiver);
                    esRegistradoBat = false;
                }
                mHourPaint.setColor(Color.DKGRAY);
                mHourPaint.setAntiAlias(false);
                mMinutePaint.setAntiAlias(false);
            }
            else{
                if(!esRegistradoBat) {
                    //registramos el broadcast de la bateria
                    IntentFilter filterCambioDeBateria = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    ServicioMain.this.registerReceiver(mBatteryReceiver, filterCambioDeBateria);
                    esRegistradoBat = true;
                }
                mHourPaint.setColor(Color.CYAN);
                mHourPaint.setAntiAlias(true);
                mMinutePaint.setAntiAlias(true);
            }
            //repintamos
            invalidate();
            //El reloj depende de si es visible o esta en ambient mode, por lo que tenemos que
            //controlar el inicio y la parada del reloj
            updateTimer();
        }


    }

}
