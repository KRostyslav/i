'use strict';

var express = require('express');
var passport = require('passport');
var request = require('request');
var authService = require('../auth.service');
var syncSubject = require('../../api/subject/subject.service');

module.exports.callback = (req, res, next)=> {
    let code = req.query.code,
        state = req.query.state;
    var getToken = ()=> {
        return new Promise((resolve, reject)=> {
            request.post("https://accounts.kitsoft.kiev.ua/oauth/token", {
                form: {
                    code,
                    client_id: req.headers.host=="localhost:8443"?8443:8933,
                    client_secret: req.headers.host=="localhost:8443"?'8443':'8933',
                    grant_type: 'authorization_code'
                }, json: true
            }, (err, resp, body)=> {
                //TODO доделать отправку токена на получение пользователя
                resolve(body)
            })
        })
    };

    var getUserId = (body)=> {
        return new Promise((resolve, reject)=> {
            request(`https://accounts.kievcity.gov.ua/user/info?access_token=${body.access_token}`, {json: true}, (error, response, data)=> {
                // console.log(data);
                resolve({user: data, access: body})
            })
        })
    }

    var getMyUser = (body)=> {
        var inn = undefined;
        if(body.user.social && body.user.social.BankID && body.user.social.BankID.inn != undefined){
            inn = body.user.social.BankID.inn
        }else if(body.user.social && body.user.social.eds && body.user.social.eds.edrpoucode != undefined){
            inn = body.user.social.eds.edrpoucode
        }else{
            inn = '3119325858'
        }


        syncSubject.sync(inn, function (error, response, data) {
            let user = {
                    customer: {
                        firstName: body.user.first_name,
                        middleName: body.user.middle_name != undefined ? body.user.middle_name : null,
                        lastName: body.user.last_name,
                    },
                    subject: data
                },
                access = {
                    accessToken: body.access.access_token,
                    refreshToken: body.access.refresh_token
                }
            req.session = authService.createSessionObject('kyivid', user, access);
            delete req.session.prepare;
            res.redirect(decodeURIComponent(state));
        })
    }

    getToken().then(getUserId).then(getMyUser)

}
module.exports.convertToCanonical = function (customer) {
    // сохранение признака для отображения надписи о необходимости проверки регистрационных данных, переданых от BankID
    // console.log(customer);
    customer.isAuthTypeFromBankID = true;
    return customer;
};
module.exports.getUserKeyFromSession = function (session){
    return session.access.accessToken;
};
