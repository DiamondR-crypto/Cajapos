#!/bin/bash
sed -i 's/fun login(username: String, pin: String): Boolean {/suspend fun login(username: String, pin: String): Boolean {/g' app/src/main/java/com/example/ui/PosViewModel.kt
sed -i '/var success = false/,/return true/c\
        val user = repository.getUserByUsername(username)\n        if (user != null && user.pin == pin) {\n            _currentUser.value = user\n            return true\n        }\n        return false\n    }\n\n    fun logout() {\n        _currentUser.value = null\n    }\n\n    fun updateUserPin(user: com.example.data.local.User, newPin: String) {\n        viewModelScope.launch {\n            repository.updateUser(user.copy(pin = newPin))\n        }\n    }' app/src/main/java/com/example/ui/PosViewModel.kt
