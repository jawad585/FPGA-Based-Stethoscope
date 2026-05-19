// Arduino Mega 2560 – 8kHz Audio ADC (LOW NOISE, 230400 BAUD)

void setup() {
  Serial.begin(230400);

  // ----- ADC SETUP -----
  ADMUX =
    (1 << REFS0) |   // AVcc reference
    1;               // ADC1 = A1  (changed from 0 to 1)

  ADCSRA =
    (1 << ADEN)  |   // Enable ADC
    (1 << ADIE)  |   // ADC interrupt
    (1 << ADPS2) | (1 << ADPS1) | (1 << ADPS0); // Prescaler 128 → 125kHz

  ADCSRB = 0;
  sei();

  // ----- TIMER1 @ 8kHz -----
  TCCR1A = 0;
  TCCR1B = 0;

  OCR1A = 249;              // 8kHz
  TCCR1B |= (1 << WGM12);   // CTC mode
  TCCR1B |= (1 << CS11);    // Prescaler = 8
  TIMSK1 |= (1 << OCIE1A);
}

ISR(TIMER1_COMPA_vect) {
  ADCSRA |= (1 << ADSC);    // Start ADC
}

ISR(ADC_vect) {
  uint8_t sample8 = ADC >> 2;  // 10-bit → 8-bit
  Serial.write(sample8);
}

void loop() {
  // fully interrupt-driven
}
