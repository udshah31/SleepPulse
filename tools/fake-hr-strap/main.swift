import CoreBluetooth
import Foundation

// Simulated BLE heart-rate strap: standard Heart Rate Service (0x180D) with the Heart Rate
// Measurement characteristic (0x2A37), notifying once a second with HR + RR-intervals.
final class Strap: NSObject, CBPeripheralManagerDelegate {
    let hrService = CBUUID(string: "180D")
    let hrMeasurement = CBUUID(string: "2A37")
    var manager: CBPeripheralManager!
    var characteristic: CBMutableCharacteristic!
    var subscribers = 0
    var beat = 0

    override init() {
        super.init()
        manager = CBPeripheralManager(delegate: self, queue: nil)
    }

    func peripheralManagerDidUpdateState(_ p: CBPeripheralManager) {
        print("state=\(p.state.rawValue)"); fflush(stdout)
        guard p.state == .poweredOn else { return }
        characteristic = CBMutableCharacteristic(type: hrMeasurement, properties: [.notify], value: nil, permissions: [])
        let service = CBMutableService(type: hrService, primary: true)
        service.characteristics = [characteristic]
        p.add(service)
    }

    func peripheralManager(_ p: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error { print("add error: \(error)"); return }
        p.startAdvertising([CBAdvertisementDataLocalNameKey: "SleepPulse-Test-HR",
                            CBAdvertisementDataServiceUUIDsKey: [hrService]])
    }

    func peripheralManagerDidStartAdvertising(_ p: CBPeripheralManager, error: Error?) {
        print(error.map { "advertise error: \($0)" } ?? "advertising as SleepPulse-Test-HR"); fflush(stdout)
        Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in self.tick() }
    }

    func peripheralManager(_ p: CBPeripheralManager, central: CBCentral, didSubscribeTo c: CBCharacteristic) {
        subscribers += 1; print("subscribed: \(central.identifier)"); fflush(stdout)
    }

    func peripheralManager(_ p: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom c: CBCharacteristic) {
        subscribers -= 1; print("unsubscribed"); fflush(stdout)
    }

    func tick() {
        guard subscribers > 0 else { return }
        beat += 1
        // ~60 bpm with alternating beat-to-beat variation, so RMSSD is clearly non-zero (~40 ms).
        let rrMs = beat % 2 == 0 ? 980.0 : 1020.0
        let rr = UInt16((rrMs * 1024.0 / 1000.0).rounded())     // units of 1/1024 s
        let hr = UInt8((60_000.0 / rrMs).rounded())
        let packet = Data([0x10, hr, UInt8(rr & 0xFF), UInt8(rr >> 8)]) // flags: uint8 HR, RR present
        let sent = manager.updateValue(packet, for: characteristic, onSubscribedCentrals: nil)
        if beat % 10 == 0 { print("beat \(beat) hr=\(hr) rr=\(Int(rrMs))ms sent=\(sent)"); fflush(stdout) }
    }
}

let strap = Strap()
RunLoop.main.run()
