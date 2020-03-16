package com.songspagetti.googlemap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapFragment
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    var googleMap:GoogleMap? = null
    var locManager:LocationManager? = null

    var lat_list = ArrayList<Double>()
    var lng_list = ArrayList<Double>()
    var name_list = ArrayList<String>()
    var vicinity_list = ArrayList<String>() // 대략적인 주소

    var marker_list = ArrayList<Marker>() // 지도에 표시할 마커

    var permission_list = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            requestPermissions(permission_list, 0)

        }else{
            init()
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for(result in grantResults){
            if(result == PackageManager.PERMISSION_DENIED){
                return
            }
        }
        init()
    }
    fun init(){
        var callback = MapReadyCallback()
        var mapFragment = fragmentManager.findFragmentById(R.id.map) as MapFragment
        mapFragment.getMapAsync(callback)
    }
//Map-fragment상에서 지도를 컨트롤 할 수 있는 구글맵 객체가 완성이 되면 onMapReady 메소드가 자동호출된다.
    inner class MapReadyCallback : OnMapReadyCallback{
        override fun onMapReady(p0: GoogleMap?) {
            googleMap = p0
            getMyLocation()
        }
    }
    //@SuppressLint("ServiceCast")
    fun getMyLocation(){
        locManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        //마시멜로우 이상 버전 SDK 사용시 권한 체크 해야함
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED){
                   return // 메소드 종료
                }
                if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED){
                    return
                }
        }

        var location = locManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        var location2 = locManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        if(location != null){
            setMyLocation(location)
        }else{
            if(location2 != null){
                setMyLocation(location2)
            }
        }
        var listener = GetMyLocationListener()
        //GPS기능 제공이 가능한지 체크하고 가능할 경우 요청한다.
        if(locManager?.isProviderEnabled(LocationManager.GPS_PROVIDER)!! == true){
            locManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10f, listener)
        }else if(locManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER)!! == true){
            locManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 10f, listener)
        }//GPS 기능 제공이 불가할 경우 네트워크 기능이 제공가능한지 체크하고 요청해야한다..
        // 각 요청에 성공할 경우 listener 로 GetMyLocationListener의 onLocationChanged 가 호출된다.
    }


//GPS 값을 받아다 지도에 띄우는데 시간이 걸리기 때문에 그전에 가장 최근에 불러온 좌표를 보여준다.
    fun setMyLocation(location : Location){
//        var lat = location.latitude
//        var lng = location.longitude

        var position = LatLng(location.latitude, location.longitude) // 현재 좌표 객체 생성
        var update1 = CameraUpdateFactory.newLatLng(position)
        var update2 = CameraUpdateFactory.zoomTo(15f) // 15배로 확대

        googleMap?.moveCamera(update1) // Repositions the camera according to the instructions defined in the update
        googleMap?.animateCamera(update2) // Animates the movement of the camera from the current position to the position defined in the update
        //println("위도 : "+location.latitude.toString()+", 경도 : "+location.longitude.toString())

        // 현재 위치를 마커로 표시하기 전에 권한 검사를 한번 더 해야한다.
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED){
                Toast.makeText(applicationContext, "ACCESS_FINE_LOCATION : Permission denied", Toast.LENGTH_LONG).show()
                return // 메소드 종료
            }
            if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED){
                Toast.makeText(applicationContext, "ACCESS_COARSE_LOCATION : Permission denied", Toast.LENGTH_LONG).show()
                return
            }
        }
        // 현재 위치 마커 표
        googleMap?.isMyLocationEnabled = true // Gets the status of the my-location layer.
        googleMap?.mapType = GoogleMap.MAP_TYPE_NORMAL
        //googleMap?.mapType =GoogleMap.MAP_TYPE_HYBRID
        //googleMap?.mapType = GoogleMap.MAP_TYPE_SATELLITE
        //googleMap?.mapType = GoogleMap.MAP_TYPE_TERRAIN

        var thread = NetworkThread(location.latitude, location.longitude)
        thread.start()

    }



    //gps 측정이 성공하면 onLocationChanged() 자동 호출
    inner class GetMyLocationListener : LocationListener{
        override fun onLocationChanged(location: Location?) {
            setMyLocation(location!!)
            //측정 중단
            locManager?.removeUpdates(this)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        override fun onProviderEnabled(provider: String?) {
        }

        override fun onProviderDisabled(provider: String?) {
        }

    }

    //네트워크를 사용하기 위해 쓰레드 생성
    inner class NetworkThread(val lat:Double, val lng:Double ):Thread(){
        override fun run() {
            var client = OkHttpClient()
            var builder = Request.Builder()
            //GET 방식 주소
            var str = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${lat},${lng}&radius=1500&type=drugstore&language=ko&key=AIzaSyBS-IEuKOMccOeTOReGUszSWKFT-CWgfTA"
            //"https://maps.googleapis.com/maps/api/place/nearbysearch/jason?location=${lat},${lng}&radius=1000&key=AIzaSyBS-IEuKOMccOeTOReGUszSWKFT-CWgfTA&type=drugstore&sensor=false&language=ko"

            var url = builder.url(str)
            var request = url.build()
            // 응답 결과에 반응하기 위해 콜백객체 생성
            var callback = Callback1()
            client.newCall(request).enqueue(callback) // 요청 성공
        }
    }
    // 요청에 대한 응답 결과에 대한 반응 작성
    inner class Callback1 : Callback {
        override fun onFailure(call: Call, e: IOException) {

        }

        override fun onResponse(call: Call, response: Response) {
            //응답결과 수신받기
            var result = response.body()?.string()
            var obj = JSONObject(result!!) // JSON 객체 생성
            var status = obj.getString("status") // 요청에 대한 메타데이터

            if(status == "OK"){
                // 한 가지에 대한 정보가 results 배열에 저장되어있다.
                var results = obj.getJSONArray("results") // results 배열 추출

                //데이터를 담기전에 초기화
                lat_list.clear()
                lng_list.clear()
                name_list.clear()
                vicinity_list.clear()

                for(i in 0 until results.length()){
                    var obj2 = results.getJSONObject(i)

                    var geometry = obj2.getJSONObject("geometry")
                    var location = geometry.getJSONObject("location")
                    var lat2 = location.getDouble("lat")
                    var lng2 = location.getDouble("lng")

                    var name = obj2.getString("name")
                    var vicinity = obj2.getString("vicinity")

                    lat_list.add(lat2)
                    lng_list.add(lng2)
                    name_list.add(name)
                    vicinity_list.add(vicinity)

                }

                runOnUiThread{
                    //marker_list 초기화
                    for(marker in marker_list){
                        marker.remove()
                    }
                    marker_list.clear()

                    for(i in 0 until lat_list.size){
                        var lat3 = lat_list.get(i)
                        var lng3 = lng_list.get(i)
                        var name3 = name_list.get(i)
                        var vicinity3 = vicinity_list.get(i)

                        var position = LatLng(lat3, lng3)
                        //마커에 대한 정보를 세팅 하는 객체 생성
                        var option = MarkerOptions()
                        //위치 설정
                        option.position(position)
                        //말풍선 설정
                        option.title(name3)
                        option.snippet(vicinity3)

                        //마커 이미지 변경
                        var bitmap = BitmapDescriptorFactory.fromResource(android.R.drawable.ic_menu_mylocation)
                        option.icon(bitmap)

                        // 마커 객체를 생성하여 세팅 --> 구글지도에 마커 찍음
                        var marker = googleMap?.addMarker(option)

                        marker_list.add(marker!!)
                    }
                }

            }
        }
    }





}
