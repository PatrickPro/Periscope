

#define PIN_FREEZEBUTTON A1               // choose the input pin (for a pushbutton)
#define PIN_FREEZELED 2
#define BLINKFREQ 100



int btnState = HIGH;      // the current btnState of the output pin
int reading;           // the current reading from the input pin
int previous = LOW;    // the previous reading from the input pin

long time = 0;         // the last time the output pin was toggled
long debounce = 400;   // the debounce time, increase if the output flickers


void FreezeButton_Init() {
  pinMode(PIN_FREEZEBUTTON, INPUT);     // declare pushbutton as input
  pinMode(PIN_FREEZELED, OUTPUT);  // declare LED as output


#ifdef DEBUG
  Serial.println("FreezeButton ready!");
#endif
}

void Read_FreezeButton(){

  reading = digitalRead(PIN_FREEZEBUTTON);

  // if the input just went from LOW and HIGH and we've waited long enough
  // to ignore any noise on the circuit, toggle the output pin and remember
  // the time
  if (reading == HIGH && previous == LOW && millis() - time > debounce ) {

    if (btnState == HIGH){
      btnState = LOW;
      freeze_btn =0;
    }
    else {
      btnState = HIGH;
      freeze_btn=1;
    }
    time = millis();    
  }
  digitalWrite(PIN_FREEZELED, btnState);
  previous = reading;




}



















