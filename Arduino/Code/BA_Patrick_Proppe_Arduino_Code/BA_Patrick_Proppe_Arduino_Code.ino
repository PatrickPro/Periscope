/*
Modified and extended Code based on

MinIMU-9-Arduino-AHRS
Pololu MinIMU-9 + Arduino AHRS (Attitude and Heading Reference System)

Copyright (c) 2011 Pololu Corporation.
http://www.pololu.com/

MinIMU-9-Arduino-AHRS is based on sf9domahrs by Doug Weibel and Jose Julio:
http://code.google.com/p/sf9domahrs/

sf9domahrs is based on ArduIMU v1.5 by Jordi Munoz and William Premerlani, Jose
Julio and Doug Weibel:
http://code.google.com/p/ardu-imu/

MinIMU-9-Arduino-AHRS is free software: you can redistribute it and/or modify it
under the terms of the GNU Lesser General Public License as published by the
Free Software Foundation, either version 3 of the License, or (at your option)
any later version.

MinIMU-9-Arduino-AHRS is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
more details.

You should have received a copy of the GNU Lesser General Public License along
with MinIMU-9-Arduino-AHRS. If not, see <http://www.gnu.org/licenses/>.


Uses MicroBridge Library:
https://code.google.com/p/microbridge/
*/


#include <Wire.h>
#include <Adb.h>


#define BAUDRATE    115200
#define DEBUG         0


// X axis pointing forward
// Y axis pointing to the left 
// and Z axis pointing up.
// Positive pitch : nose down
// Positive roll : right wing down
// Positive yaw : counterclockwise

int SENSOR_SIGN[9] = {  
  1,-1,-1,-1,1,1,1,-1,-1}; //Correct directions x,y,z - gyro, accelerometer, magnetometer


// LSM303 accelerometer: 8 g sensitivity
// 3.8 mg/digit; 1 g = 256
#define GRAVITY 256  
#define ToRad(x) ((x)*0.01745329252)  // *pi/180
#define ToDeg(x) ((x)*57.2957795131)  // *180/pi


// L3G4200D gyro: 2000 dps full scale
// 70 mdps/digit; 1 dps = 0.07

#define Gyro_Gain_X 0.07 //X axis Gyro gain
#define Gyro_Gain_Y 0.07 //Y axis Gyro gain
#define Gyro_Gain_Z 0.07 //Z axis Gyro gain
#define Gyro_Scaled_X(x) ((x)*ToRad(Gyro_Gain_X)) //Return the scaled ADC raw data of the gyro in radians for second
#define Gyro_Scaled_Y(x) ((x)*ToRad(Gyro_Gain_Y)) //Return the scaled ADC raw data of the gyro in radians for second
#define Gyro_Scaled_Z(x) ((x)*ToRad(Gyro_Gain_Z)) //Return the scaled ADC raw data of the gyro in radians for second

// LSM303 magnetometer calibration constants; use the Calibrate example from
// the Pololu LSM303 library to find the right values for your board

#define M_X_MIN -421
#define M_Y_MIN -639
#define M_Z_MIN -238
#define M_X_MAX 424
#define M_Y_MAX 295
#define M_Z_MAX 472

#define Kp_ROLLPITCH 0.02
#define Ki_ROLLPITCH 0.00002
#define Kp_YAW 1.2
#define Ki_YAW 0.00002

float G_Dt=0.02;    // Integration time (DCM algorithm)  We will run the integration loop at 50Hz if possible
long timer=0;   //general purpuse timer
long counter_adb=0;   //adb  timer
long timer_old;
long timer24=0; //Second timer used to print values 
int AN[6]; //array that stores the gyro and accelerometer data
int AN_OFFSET[6]={
  0,0,0,0,0,0}; //Array that stores the Offset of the sensors

int gyro_x;
int gyro_y;
int gyro_z;
int accel_x;
int accel_y;
int accel_z;
int magnetom_x;
int magnetom_y;
int magnetom_z;

float c_magnetom_x;
float c_magnetom_y;
float c_magnetom_z;

float MAG_Heading;

float Accel_Vector[3]= {
  0,0,0}; //Store the acceleration in a vector

float Gyro_Vector[3]= {
  0,0,0};//Store the gyros turn rate in a vector

float Omega_Vector[3]= {
  0,0,0}; //Corrected Gyro_Vector data

float Omega_P[3]= {
  0,0,0};//Omega Proportional correction

float Omega_I[3]= {
  0,0,0};//Omega Integrator

float Omega[3]= {
  0,0,0};

// Euler angles
float roll;
float pitch;
float yaw;
int normalized_yaw;

float errorRollPitch[3]= {
  0,0,0}; 
float errorYaw[3]= {
  0,0,0};

unsigned int counter=0;
byte gyro_sat=0;

float DCM_Matrix[3][3]= {
  {
    1,0,0                                      }
  ,{
    0,1,0                                      }
  ,{
    0,0,1                                      }
}; 

float Update_Matrix[3][3]={
  {
    0,1,2                                      }
  ,{
    3,4,5                                      }
  ,{
    6,7,8                                      }
}; 

float Temporary_Matrix[3][3]={
  {
    0,0,0                                      }
  ,{
    0,0,0                                      }
  ,{
    0,0,0                                      }
};

//int encoder_cnt = 16382; // ungefähr die hälfte von integer
 long encoder_cnt = 1073741824L;
int freeze_btn = 0;


// ------- SETUP -------



void setup()
{ 

  Serial.begin(BAUDRATE);
  I2C_Init();
  Serial.println("Pololu MinIMU-9 + Arduino AHRS");
  delay(1500);

  Accel_Init();
  Compass_Init();
  Gyro_Init();
  Encoder_Init();
  FreezeButton_Init();
  ADB_Init();
  delay(20);

  for(int i=0;i<32;i++)    // We take some readings...
  {

    Read_Gyro();
    Read_Accel();
    for(int y=0; y<6; y++)   // Cumulate values
      AN_OFFSET[y] += AN[y];
    delay(20);
  }

  for(int y=0; y<6; y++)
    AN_OFFSET[y] = AN_OFFSET[y]/32;
  AN_OFFSET[5]-=GRAVITY*SENSOR_SIGN[5];

#ifdef DEBUG 
  for(int y=0; y<6; y++)
    Serial.println(AN_OFFSET[y]);
#endif 

  delay(2000);

  timer=millis();
  delay(20);
  counter=0;
}





// ------- LOOP -------

void loop() //Main Loop
{


 Read_Encoder();
  if((millis()-timer)>=20)  // Main loop runs at 50Hz
  {

    counter++;
    counter_adb++;

    timer_old = timer;
    timer=millis();
    if (timer>timer_old)
      G_Dt = (timer-timer_old)/1000.0;    // Real time of loop run. We use this on the DCM algorithm M(gyro integration time)
    else
      G_Dt = 0;

    Read_Gyro();   // This read gyro data
    Read_Accel();     // Read I2C accelerometer

    if (counter > 5)  // Read compass data at 10Hz... (5 loop runs)
    {
      counter=0;
      Read_Compass();    // Read I2C magnetometer
      Compass_Heading(); // Calculate magnetic heading  
    }

    
   
    Read_FreezeButton(); 

    Matrix_update(); 
    Normalize();
    Drift_correction();
    Euler_angles();

    NormalizeYaw(); //Umrechnung auf 360 Grad Kompass


    if (counter_adb > 3) 
    {
      counter_adb=0;
      sendDataViaADB();

    }


  }









}























