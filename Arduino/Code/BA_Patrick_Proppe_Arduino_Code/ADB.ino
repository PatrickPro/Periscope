/* 
This code is used for interconnecting the Arduino with the
Android device through port 4568.
Through this port are send the pitch, roll and yaw values 
from the IMU, paired with the state of encoder and button.  
*/

#include <SPI.h>
#include <Adb.h>

#define TCP_PORT "tcp:4568"

Connection * connection;


// Event handler for the shell connection. 
void adbEventHandler (Connection * connection, adb_eventType event, uint16_t length, uint8_t * data)
{
  // not needed in this setup
}



void ADB_Init()
{

  // Initialise the ADB subsystem.  
  ADB::init();
  connection = ADB::addConnection(TCP_PORT, true, adbEventHandler);  

  // TODO: ERROR Handling

#ifdef DEBUG
  Serial.println("ADB ready!");
#endif 

}

void sendDataViaADB()
{

  String strRoll =  String(int ToDeg(roll));                       
  String strPitch = String(int ToDeg(pitch));  
 
  String tmpString =  String(strRoll + "," + strPitch + "," + normalized_yaw + ","  + encoder_cnt + "," + freeze_btn + ",");

  int ss_size = tmpString.length()+1;
  char sendstring[ss_size];
  tmpString.toCharArray(sendstring, ss_size );

  connection->writeString(sendstring);
  Serial.println(sendstring);
  //Serial.println(ToDeg(yaw));
  //Serial.println(normalized_yaw);

  ADB::poll();
}














