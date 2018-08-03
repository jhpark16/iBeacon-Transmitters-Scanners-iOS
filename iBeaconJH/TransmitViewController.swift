//
//  TransmitViewController.swift
//  iBeaconJH
//
//  Created by Jungho Park on 10/12/17.
//  Copyright © 2017 Jungho Park. All rights reserved.
//

import UIKit
import CoreLocation
import CoreBluetooth

class TransmitViewController: UIViewController, CBPeripheralManagerDelegate {

    @IBOutlet weak var uuidLabel: UILabel!
    @IBOutlet weak var majorLabel: UILabel!
    @IBOutlet weak var minorLabel: UILabel!
    @IBOutlet weak var identityLabel: UILabel!
    var beaconRegion: CLBeaconRegion!
    var beaconPeripheralData: NSDictionary!
    var peripheralManager: CBPeripheralManager!
    
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        if (peripheral.state == .poweredOn) {
            peripheralManager.startAdvertising(beaconPeripheralData as? [String : Any])
            print("Bluetooth is on")
        } else {
            peripheralManager .stopAdvertising()
            print("Bluetooth is off, or some other error")
        }
    }
    
    func initBeaconRegion() {
        beaconRegion = CLBeaconRegion.init(proximityUUID: UUID.init(uuidString: NSLocalizedString("iBeaconUUID", comment: ""))!,                                          major: 1, minor: 1,
            identifier: NSLocalizedString("iBeaconID", comment: ""))
    }
    
    func updateLabels() {
        uuidLabel.text = beaconRegion.proximityUUID.uuidString
        majorLabel.text = beaconRegion.major?.stringValue
        minorLabel.text = beaconRegion.minor?.stringValue
        identityLabel.text = beaconRegion.identifier
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()

        // Do any additional setup after loading the view.
        initBeaconRegion()
        updateLabels()
    }

    @IBAction func transmitButtonTapped(_ sender: UIButton) {
        beaconPeripheralData = beaconRegion.peripheralData(withMeasuredPower: nil)
        peripheralManager = CBPeripheralManager.init(delegate: self, queue: nil)
    }
    
    override func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }
}
