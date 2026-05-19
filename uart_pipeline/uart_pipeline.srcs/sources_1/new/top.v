//============================================================
// MODULE: uart_rx_core
//============================================================
module uart_rx_core #(
    parameter CLK_FREQ  = 100_000_000,
    parameter BAUD_RATE = 230_400
)(
    input  wire       clk,
    input  wire       uart_rx,
    output wire [7:0] rx_byte,
    output wire       rx_ready
);
    localparam BAUD_TICK = CLK_FREQ / BAUD_RATE;

    reg [13:0] rx_cnt      = 0;
    reg  [3:0] rx_bit_cnt  = 0;
    reg  [7:0] rx_shift    = 0;
    reg        rx_busy     = 0;
    reg        rx_ready_r  = 0;
    reg  [7:0] rx_byte_r   = 0;
    reg        sync1 = 1, sync2 = 1;

    always @(posedge clk) begin
        sync1 <= uart_rx;
        sync2 <= sync1;
    end

    always @(posedge clk) begin
        if (!rx_busy && sync2 == 0) begin
            rx_busy    <= 1;
            rx_cnt     <= BAUD_TICK / 2;
            rx_bit_cnt <= 0;
        end else if (rx_busy) begin
            if (rx_cnt == BAUD_TICK - 1) begin
                rx_cnt     <= 0;
                rx_bit_cnt <= rx_bit_cnt + 1;
                if (rx_bit_cnt < 8)
                    rx_shift <= {sync2, rx_shift[7:1]};
                else begin
                    rx_byte_r  <= rx_shift;
                    rx_ready_r <= 1;
                    rx_busy    <= 0;
                end
            end else
                rx_cnt <= rx_cnt + 1;
        end else
            rx_ready_r <= 0;
    end

    assign rx_byte  = rx_byte_r;
    assign rx_ready = rx_ready_r;
endmodule


//============================================================
// MODULE: uart_tx_core
//============================================================
module uart_tx_core #(
    parameter CLK_FREQ  = 100_000_000,
    parameter BAUD_RATE = 230_400
)(
    input  wire       clk,
    input  wire       tx_start,
    input  wire [7:0] tx_byte,
    output wire       uart_tx,
    output wire       tx_busy
);
    localparam BAUD_TICK = CLK_FREQ / BAUD_RATE;

    reg [13:0] tx_cnt     = 0;
    reg  [3:0] tx_bit_cnt = 0;
    reg  [9:0] tx_shift   = 10'b1111111111;
    reg        tx_busy_r  = 0;

    assign uart_tx = tx_shift[0];
    assign tx_busy = tx_busy_r;

    always @(posedge clk) begin
        if (!tx_busy_r && tx_start) begin
            tx_shift   <= {1'b1, tx_byte, 1'b0};
            tx_bit_cnt <= 10;
            tx_busy_r  <= 1;
            tx_cnt     <= 0;
        end else if (tx_busy_r) begin
            if (tx_cnt == BAUD_TICK - 1) begin
                tx_cnt     <= 0;
                tx_shift   <= {1'b1, tx_shift[9:1]};
                tx_bit_cnt <= tx_bit_cnt - 1;
                if (tx_bit_cnt == 1)
                    tx_busy_r <= 0;
            end else
                tx_cnt <= tx_cnt + 1;
        end
    end
endmodule


//============================================================
// MODULE: iir_filter  (2nd-order IIR)
//============================================================
module iir_filter (
    input  wire              clk,
    input  wire              rx_ready,
    input  wire        [7:0] rx_byte,
    input  wire              tx_busy,
    output reg  signed [15:0] y0,
    output reg               tx_start,
    output reg         [7:0] tx_byte
);
    parameter signed [15:0] B0    =  16'd30;
    parameter signed [15:0] B1    =  16'd30;
    parameter signed [15:0] B2    =  16'd0;
    parameter signed [15:0] A1    = -16'd50;
    parameter signed [15:0] A2    =  16'd20;
    parameter signed [15:0] SCALE =  16'd128;

    reg signed [15:0] x0, x1 = 0, x2 = 0;
    reg signed [15:0]     y1 = 0, y2 = 0;

    always @(posedge clk) begin
        if (rx_ready) begin
            x0 <= rx_byte - 8'd128;
            y0 <= (B0*x0 + B1*x1 + B2*x2 - A1*y1 - A2*y2) / SCALE;

            if (y0 >  127) y0 <=  127;
            else if (y0 < -128) y0 <= -128;

            x2 <= x1; x1 <= x0;
            y2 <= y1; y1 <= y0;

            tx_byte  <= y0 + 8'd128;
            tx_start <= !tx_busy;
        end else
            tx_start <= 0;
    end
endmodule


//============================================================
// MODULE: led_equalizer
//============================================================
module led_equalizer (
    input  wire              clk,
    input  wire              rx_ready,
    input  wire signed [15:0] y0,
    output reg  [15:0]       led
);
    reg signed [15:0] audio_temp;
    reg  [3:0]        led_level;
    integer i;

    always @(posedge clk) begin
        if (rx_ready) begin
            audio_temp = y0;
            if (audio_temp < 0)   audio_temp = -audio_temp;
            if (audio_temp > 127) audio_temp = 127;

            led_level <= audio_temp >> 3;

            for (i = 0; i < 16; i = i + 1)
                led[i] <= (i < led_level);
        end
    end
endmodule


//============================================================
// MODULE: ms_tick_gen
//============================================================
module ms_tick_gen #(
    parameter CLK_FREQ = 100_000_000
)(
    input  wire        clk,
    output reg         ms_tick = 0,
    output reg  [15:0] time_ms = 0
);
    localparam MS_COUNT = CLK_FREQ / 1000;

    reg [16:0] ms_cnt = 0;

    always @(posedge clk) begin
        if (ms_cnt == MS_COUNT - 1) begin
            ms_cnt  <= 0;
            ms_tick <= 1;
        end else begin
            ms_cnt  <= ms_cnt + 1;
            ms_tick <= 0;
        end
    end

    always @(posedge clk)
        if (ms_tick) time_ms <= time_ms + 1;
endmodule


//============================================================
// MODULE: heartbeat_detect
//============================================================
module heartbeat_detect (
    input  wire              clk,
    input  wire              rx_ready,
    input  wire              ms_tick,
    input  wire [15:0]       time_ms,
    input  wire signed [15:0] y0,
    output reg  [9:0]        bpm_int = 0
);
    localparam MA_SIZE          = 50;
    localparam signed THRESHOLD = 16'd40;

    reg signed [15:0] ma_buf [0:MA_SIZE-1];
    reg  [5:0]        ma_idx  = 0;
    reg signed [31:0] ma_sum  = 0;
    reg signed [15:0] baseline;

    reg [15:0] last_peak = 0;
    reg [15:0] prev_peak = 0;
    reg [15:0] interval;

    always @(posedge clk) begin
        if (rx_ready) begin
            ma_sum         <= ma_sum - ma_buf[ma_idx] + y0;
            ma_buf[ma_idx] <= y0;
            ma_idx         <= (ma_idx == MA_SIZE - 1) ? 0 : ma_idx + 1;
            baseline       <= ma_sum / MA_SIZE;

            if ((y0 - baseline) > THRESHOLD) begin
                if ((time_ms - last_peak) > 300) begin
                    prev_peak <= last_peak;
                    last_peak <= time_ms;

                    if (prev_peak != 0) begin
                        interval = last_peak - prev_peak;
                        if (interval != 0) begin
                            bpm_int <= 60000 / interval;
                            if (bpm_int > 999) bpm_int <= 999;
                        end
                    end
                end
            end
        end
    end
endmodule


//============================================================
// MODULE: seg7_display
//============================================================
module seg7_display (
    input  wire       clk,
    input  wire [9:0] bpm_int,
    output reg  [6:0] seg,
    output reg  [7:0] an
);
    reg [16:0] refresh_cnt = 0;
    reg  [1:0] digsel;
    reg  [3:0] digit;

    always @(posedge clk)
        refresh_cnt <= refresh_cnt + 1;

    always @(*) digsel = refresh_cnt[16:15];

    always @(*) begin
        an = 8'b11111111;
        case (digsel)
            2'd0: begin an[0] = 0; digit = bpm_int % 10;        end
            2'd1: begin an[1] = 0; digit = (bpm_int / 10) % 10; end
            2'd2: begin an[2] = 0; digit = bpm_int / 100;        end
            default: digit = 0;
        endcase
    end

    always @(*) begin
        case (digit)
            4'd0: seg = 7'b1000000;
            4'd1: seg = 7'b1111001;
            4'd2: seg = 7'b0100100;
            4'd3: seg = 7'b0110000;
            4'd4: seg = 7'b0011001;
            4'd5: seg = 7'b0010010;
            4'd6: seg = 7'b0000010;
            4'd7: seg = 7'b1111000;
            4'd8: seg = 7'b0000000;
            4'd9: seg = 7'b0010000;
            default: seg = 7'b1111111;
        endcase
    end
endmodule


//============================================================
// MODULE: top  (instantiates all submodules)
//============================================================
module top (
    input  wire        clk,
    input  wire        uart_rx,
    output wire        uart_tx,
    output wire [15:0] led,
    output wire [6:0]  seg,
    output wire [7:0]  an
);
    parameter CLK_FREQ  = 100_000_000;
    parameter BAUD_RATE = 230_400;

    // Internal wires
    wire [7:0]         rx_byte;
    wire               rx_ready;
    wire               tx_busy;
    wire               tx_start;
    wire [7:0]         tx_byte;
    wire signed [15:0] y0;
    wire               ms_tick;
    wire [15:0]        time_ms;
    wire [9:0]         bpm_int;

    uart_rx_core #(
        .CLK_FREQ  (CLK_FREQ),
        .BAUD_RATE (BAUD_RATE)
    ) u_rx (
        .clk      (clk),
        .uart_rx  (uart_rx),
        .rx_byte  (rx_byte),
        .rx_ready (rx_ready)
    );

    uart_tx_core #(
        .CLK_FREQ  (CLK_FREQ),
        .BAUD_RATE (BAUD_RATE)
    ) u_tx (
        .clk      (clk),
        .tx_start (tx_start),
        .tx_byte  (tx_byte),
        .uart_tx  (uart_tx),
        .tx_busy  (tx_busy)
    );

    iir_filter u_filter (
        .clk      (clk),
        .rx_ready (rx_ready),
        .rx_byte  (rx_byte),
        .tx_busy  (tx_busy),
        .y0       (y0),
        .tx_start (tx_start),
        .tx_byte  (tx_byte)
    );

    led_equalizer u_led (
        .clk      (clk),
        .rx_ready (rx_ready),
        .y0       (y0),
        .led      (led)
    );

    ms_tick_gen #(
        .CLK_FREQ (CLK_FREQ)
    ) u_ms (
        .clk      (clk),
        .ms_tick  (ms_tick),
        .time_ms  (time_ms)
    );

    heartbeat_detect u_bpm (
        .clk      (clk),
        .rx_ready (rx_ready),
        .ms_tick  (ms_tick),
        .time_ms  (time_ms),
        .y0       (y0),
        .bpm_int  (bpm_int)
    );

    seg7_display u_seg (
        .clk     (clk),
        .bpm_int (bpm_int),
        .seg     (seg),
        .an      (an)
    );

endmodule