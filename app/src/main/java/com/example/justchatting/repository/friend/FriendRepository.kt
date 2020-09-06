package com.example.justchatting.repository.friend

import android.app.Application
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.justchatting.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class FriendRepository {
    companion object{
        val TAG = "FriendRepository"
    }

    var friendMap : HashMap<String, User> = HashMap()
    private var _users : MutableLiveData<ArrayList<User>> = MutableLiveData(ArrayList())
    val users : LiveData<ArrayList<User>>
        get() = _users

    private var myUser : MutableLiveData<User> = MutableLiveData()

    var addFriend : MutableLiveData<Int> = MutableLiveData()


    fun loadFriends() {
        val uid = FirebaseAuth.getInstance().uid
        val ref = FirebaseDatabase.getInstance().getReference("/friends/$uid")
        ref.addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onCancelled(error: DatabaseError) {
            }

            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach{dataSnapshot ->
                    val data = dataSnapshot.getValue(Boolean::class.java)?: return
                    if(data)
                    {
                        val friendId = dataSnapshot.key
                        val friendUserRef =  FirebaseDatabase.getInstance().getReference("/users/$friendId")
                        friendUserRef.addListenerForSingleValueEvent(object : ValueEventListener{
                            override fun onCancelled(error: DatabaseError) {
                            }
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val user = snapshot.getValue(User::class.java)?: return
                                friendMap[friendId!!] = user
                            }
                        })
                    }
                }

                _users.postValue(getUsersArrayList())
            }
        })
    }
    fun getUsersArrayList(): ArrayList<User> {
        val arrayList = ArrayList(friendMap.values)
        arrayList.sortWith(compareBy{it.username})
        arrayList.add(0, myUser.value)
        return arrayList
    }

    fun makeFriendRelationships(application: Application){
        var uid = FirebaseAuth.getInstance().uid
        val myUserRef = FirebaseDatabase.getInstance().getReference("/users/$uid")

        myUserRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onCancelled(error: DatabaseError) {
            }
            override fun onDataChange(snapshot: DataSnapshot) {
                val myUser = snapshot.getValue(User::class.java)?: return

                val contactList = getContacts(application)
                contactList.forEachIndexed { _, number ->
                    val ref = FirebaseDatabase.getInstance().getReference("/phone/$number")
                    Log.d(TAG, "number : $number")
                    ref.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onCancelled(error: DatabaseError) {}
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val friendId = snapshot.getValue(String::class.java) ?: return
                            val fromRef = FirebaseDatabase.getInstance()
                                .getReference("/friends/${uid}/${friendId}")
                            fromRef.setValue(true)
                            if (uid != friendId) {
                                val toRef = FirebaseDatabase.getInstance()
                                    .getReference("/friends/${friendId}/$uid")
                                toRef.setValue(true)
                            }
                        }
                    })
                }
            }
        })
    }
    private fun getContacts(application: Application): ArrayList<String> {
        val contactList = ArrayList<String>()
        val cr = application.contentResolver
        val cur: Cursor? = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null)
        if ((cur?.count ?: 0) > 0) {
            while (cur != null && cur.moveToNext()) {
                val id: String = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID))
                val name: String = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                if (cur.getInt(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                    val pCur: Cursor? = cr.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        arrayOf(id),
                        null
                    )
                    while (pCur!!.moveToNext()) {
                        val temp : String = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        var phoneNo : String = ""
                        temp.forEach {
                            if(it.isDigit())
                                phoneNo+=it
                        }
                        contactList.add(phoneNo)
                    }
                    pCur.close()
                }
            }
        }
        cur?.close()
        return contactList
    }

    fun addFriendWithEmail(email : String){
        if(email.isEmpty())
            return
        val uid = FirebaseAuth.getInstance().uid
        val friendEmailRef = FirebaseDatabase.getInstance().getReference("/email/$email")
        friendEmailRef.addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onCancelled(error: DatabaseError) {
            }
            override fun onDataChange(snapshot: DataSnapshot) {
                val friendId = snapshot.getValue(String()::class.java)
                if(friendId == null){
                    addFriend.postValue(-1)
                    return
                }
                val fromUserFriendRef = FirebaseDatabase.getInstance().getReference("/friends/$uid/$friendId")
                fromUserFriendRef.setValue(true)
                val toUserFriendRef = FirebaseDatabase.getInstance().getReference("/friends/$friendId/$uid")
                toUserFriendRef.setValue(true)
                addFriend.postValue(1)
            }
        })
    }
    fun addFriendWithPhoneNumber(phoneNum : String){
        if(phoneNum.isEmpty())
            return

        val uid = FirebaseAuth.getInstance().uid
        val friendPhoneRef = FirebaseDatabase.getInstance().getReference("/phone/$phoneNum")
        friendPhoneRef.addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onCancelled(error: DatabaseError) {
            }
            override fun onDataChange(snapshot: DataSnapshot) {
                val friendId = snapshot.getValue(String()::class.java)
                if(friendId == null){
                    addFriend.postValue(-1)
                    return
                }
                val fromUserFriendRef = FirebaseDatabase.getInstance().getReference("/friends/$uid/$friendId")
                fromUserFriendRef.setValue(true)
                val toUserFriendRef = FirebaseDatabase.getInstance().getReference("/friends/$friendId/$uid")
                toUserFriendRef.setValue(true)
                addFriend.postValue(1)
            }
        })
    }
    fun setListener()
    {
        val uid = FirebaseAuth.getInstance().uid
        val myRef = FirebaseDatabase.getInstance().getReference("/users/$uid")
        myRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(error: DatabaseError) {
            }

            override fun onDataChange(snapshot: DataSnapshot) {
                val my = snapshot.getValue(User::class.java) ?: return
                myUser.postValue(my)
            }
        })

        val ref = FirebaseDatabase.getInstance().getReference("/friends/$uid")
        ref.addChildEventListener(object : ChildEventListener {
            override fun onCancelled(error: DatabaseError) {
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {

            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
            }
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {

                val friend = snapshot.getValue(Boolean::class.java) ?: return
                if(friend)
                {
                    val friendId = snapshot.key?: return
                    val ref = FirebaseDatabase.getInstance().getReference("/users/$friendId")
                    ref.addListenerForSingleValueEvent(object : ValueEventListener{
                        override fun onCancelled(error: DatabaseError) {
                        }

                        override fun onDataChange(snapshot: DataSnapshot) {
                            val user = snapshot.getValue(User::class.java)?:return
                            friendMap[friendId] = user
                            _users.postValue(getUsersArrayList())
                        }
                    })
                }
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
            }
        })
    }
}