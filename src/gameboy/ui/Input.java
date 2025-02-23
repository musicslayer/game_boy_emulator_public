package gameboy.ui;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import gameboy.data.SignalConsumer;
import gameboy.data.SignalProducer;
import gameboy.emulator.interaction.Controller;

public class Input implements KeyListener, SignalProducer {
    Controller controller;

    public Input(Controller controller) {
        this.controller = controller;
        addSignalConsumer(controller);
    }

    // Up = 38
    // Down = 40
    // Left = 37
    // Right = 39

    // z (B) = 90
    // x (A) = 88
    // q (SELECT) = 81
    // w (START) = 87

    public int getButton(int key) {
        // Only react to keys that are mapped to Gameboy buttons.
        int button;
        switch(key) {
        case 38:
            button = Controller.BUTTON_UP;
            break;
        case 40:
            button = Controller.BUTTON_DOWN;
            break;
        case 37:
            button = Controller.BUTTON_LEFT;
            break;
        case 39:
            button = Controller.BUTTON_RIGHT;
            break;
        case 90:
            button = Controller.BUTTON_B;
            break;
        case 88:
            button = Controller.BUTTON_A;
            break;
        case 81:
            button = Controller.BUTTON_SELECT;
            break;
        case 87:
            button = Controller.BUTTON_START;
            break;


        default:
            button = Controller.BUTTON_OTHER;
        }

        return button;
    }
    
    // KeyListener
    public void keyPressed(KeyEvent keyEvent) {
        int button = getButton(keyEvent.getKeyCode());
        produceSignal(Controller.ACTION_PRESS, button);
    }

    public void keyReleased(KeyEvent keyEvent) {
        int button = getButton(keyEvent.getKeyCode());
        produceSignal(Controller.ACTION_RELEASE, button);
    }

    public void keyTyped(KeyEvent keyEvent) {
        // Do nothing.
    }

    // SignalProducer
    SignalConsumer[] signalConsumers = new SignalConsumer[0];

    @Override
    public SignalConsumer[] getSignalConsumers() {
        return signalConsumers;
    }

    @Override
    public void addSignalConsumer(SignalConsumer signalConsumer) {
        // Create a new array with one more element.
        SignalConsumer[] oldArray = signalConsumers;
        signalConsumers = new SignalConsumer[oldArray.length + 1];

        // Copy over elements from old array.
        for(int i = 0; i < oldArray.length; i++) {
            signalConsumers[i] = oldArray[i];
        }

        // Add new element.
        signalConsumers[oldArray.length] = signalConsumer;
    }
}
