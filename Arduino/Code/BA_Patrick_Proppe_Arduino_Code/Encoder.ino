#define PIN_HIGHBIT (5)
#define PIN_PWR     (3)
#define PIN_LOWBIT  (4)



int state, prevState = 0;
long count = 1073741824L;
/* old state, new state, change (+ means clockwise)
 * 0 2 +
 * 1 0 +
 * 2 3 +
 * 3 1 +
 * 0 1 -
 * 1 3 -
 * 2 0 -
 * 3 2 -
 */
int encoderStates[4][4] = {
  {  
    0, -1,  1,  0             }
  , 
  {  
    1,  0,  0, -1             }
  , 
  { 
    -1,  0,  0,  1             }
  , 
  {  
    0,  1, -1,  0             }
  , 
};


void Encoder_Init() {
  pinMode(PIN_HIGHBIT, INPUT);
  pinMode(PIN_LOWBIT, INPUT);
  pinMode(PIN_PWR, OUTPUT);
  digitalWrite(PIN_PWR, LOW);
  digitalWrite(PIN_LOWBIT, HIGH);
  digitalWrite(PIN_HIGHBIT, HIGH);



}

void Read_Encoder() {
  state = (digitalRead(PIN_HIGHBIT) << 1) | digitalRead(PIN_LOWBIT);
  count += encoderStates[prevState][state];

  if (state != prevState) {

    encoder_cnt = count;
  }


  prevState = state;

  if (encoder_cnt<0){
    encoder_cnt = 0;
    count = 0;
  }

}






