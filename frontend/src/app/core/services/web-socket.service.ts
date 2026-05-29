import { Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private client: Client;
  private connectionSubject = new Subject<void>();
  connection$ = this.connectionSubject.asObservable();

  constructor() {
    this.client = new Client({
      brokerURL: undefined,
      webSocketFactory: () => new WebSocket(`${environment.wsUrl}/websocket`),
      reconnectDelay: 5000,
      onConnect: () => {
        console.log('WebSocket connected');
        this.connectionSubject.next();
      },
      onDisconnect: () => {
        console.log('WebSocket disconnected');
      }
    });
  }

  connect() {
    this.client.activate();
  }

  disconnect() {
    this.client.deactivate();
  }

  subscribe(topic: string, callback: (message: any) => void) {
    return this.client.subscribe(topic, (msg: IMessage) => {
      callback(JSON.parse(msg.body));
    });
  }
}
